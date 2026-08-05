package ca.vicilearning.dashboard.association;

import ca.vicilearning.dashboard.domain.FamilyAssociation;
import ca.vicilearning.dashboard.domain.FamilyAssociationRepository;
import ca.vicilearning.dashboard.domain.RosterStudent;
import ca.vicilearning.dashboard.domain.RosterStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Association Account feature (Sara's #1 ask): the dashboard is the authoritative family ↔
 * student map. Confirmed onsite (2026-07-30) that Brevo has <b>no usable family field</b> (only
 * Segments), so the family assignment is entirely staff-owned here.
 *
 * <p>Operates on the Brevo-sourced <b>{@link RosterStudent}</b> roster — each real student, keyed by
 * its {@code EXT_ID}. A student's <b>family</b> is its {@code accountId} (the {@code Surname_Account}
 * key, shared by siblings); a student with no {@code accountId} is <b>unassigned</b> and waits in a
 * queue for staff to assign it. Each family also has a first-class {@link FamilyAssociation} row
 * (name, notes), auto-created the first time the family is seen.
 */
@Service
public class AssociationService {

    private final RosterStudentRepository rosterStudentRepo;
    private final FamilyAssociationRepository familyRepo;

    public AssociationService(RosterStudentRepository rosterStudentRepo,
                              FamilyAssociationRepository familyRepo) {
        this.rosterStudentRepo = rosterStudentRepo;
        this.familyRepo = familyRepo;
    }

    /** Students not yet assigned to a family — the assignment queue. */
    public List<StudentView> unassignedStudents() {
        return rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNull().stream()
                .sorted(Comparator.comparing(RosterStudent::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(AssociationService::toView)
                .toList();
    }

    /**
     * Assigned students rolled up by family (Account_ID), families sorted by key, members by name.
     * Each family carries its {@link FamilyAssociation} name/notes; a family seen for the first time
     * gets its row auto-created here (idempotent get-or-create). Transactional because of that create.
     */
    @Transactional
    public List<FamilyView> families() {
        Map<String, List<StudentView>> byAccount = new LinkedHashMap<>();
        rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull().stream()
                .sorted(Comparator.comparing(RosterStudent::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .forEach(s -> byAccount.computeIfAbsent(s.getAccountId().trim(), k -> new ArrayList<>())
                        .add(toView(s)));

        // Bulk-load the family rows that already exist, so we only create the genuinely-new ones.
        Map<String, FamilyAssociation> existing = familyRepo.findAllById(byAccount.keySet()).stream()
                .collect(Collectors.toMap(FamilyAssociation::getAccountId, f -> f));

        return byAccount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(e -> {
                    FamilyAssociation fam = existing.get(e.getKey());
                    if (fam == null) {
                        fam = createFamily(e.getKey());
                    }
                    return new FamilyView(e.getKey(), fam.getName(), fam.getNotes(), e.getValue());
                })
                .toList();
    }

    /** Distinct existing family keys, for populating the assignment dropdown. */
    public List<String> existingFamilyKeys() {
        return families().stream().map(FamilyView::accountId).toList();
    }

    /**
     * Assign a student (by EXT_ID) to a family by setting its Account_ID, and ensure that family has a
     * {@link FamilyAssociation} row (created if the key is new). The staff-typed key is normalized so a
     * spelling like {@code "Gray"} folds into an existing {@code "Gray_Account"} family instead of
     * forking a second one (see {@link #resolveFamilyKey}). Also handles a <b>move</b>: assigning an
     * already-assigned student simply repoints it. Blank input is a no-op so an empty form submission
     * can't wipe an assignment.
     */
    @Transactional
    public void assignToFamily(String extId, String accountId) {
        if (isBlank(extId) || isBlank(accountId)) {
            return;
        }
        String key = resolveFamilyKey(accountId);
        if (key == null) {
            return;
        }
        rosterStudentRepo.findById(extId).ifPresent(s -> {
            s.setAccountId(key);
            rosterStudentRepo.save(s);
        });
        getOrCreateFamily(key);
    }

    /**
     * Remove a student's family assignment (by EXT_ID), returning it to the unassigned queue. Clears
     * the Account_ID; the roster sync's carry-over only preserves a <b>non-null</b> staff key, so this
     * unassignment survives the next sync (the student stays unassigned until staff re-assign it).
     * No-op for a blank/unknown id.
     */
    @Transactional
    public void unassign(String extId) {
        if (isBlank(extId)) {
            return;
        }
        rosterStudentRepo.findById(extId).ifPresent(s -> {
            s.setAccountId(null);
            rosterStudentRepo.save(s);
        });
    }

    /**
     * Set a family's staff-editable name and notes (creating the family row if needed), stamping
     * {@code updatedAt}. Blank name/notes are stored as null so the view falls back to the raw
     * Account_ID / hides the notes line. No-op when the account key is blank.
     */
    @Transactional
    public void updateFamily(String accountId, String name, String notes) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        FamilyAssociation fam = getOrCreateFamily(accountId.trim());
        fam.setName(blankToNull(name));
        fam.setNotes(blankToNull(notes));
        fam.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        familyRepo.save(fam);
    }

    /**
     * Rename a family to a new Account_ID spelling: repoints every assigned member from the old key to
     * the resolved new key and moves the family's name/notes onto the new key's row, deleting the old
     * one. If the new spelling resolves to an <b>existing different</b> family, this becomes a merge
     * into it (that family keeps its own name/notes). No-op when either key is blank or they resolve to
     * the same family.
     */
    @Transactional
    public void renameFamily(String oldAccountId, String newAccountId) {
        if (isBlank(oldAccountId) || isBlank(newAccountId)) {
            return;
        }
        String oldKey = oldAccountId.trim();
        String newKey = resolveFamilyKey(newAccountId);
        if (newKey == null || newKey.equals(oldKey)) {
            return;
        }
        repointFamily(oldKey, newKey);
    }

    /**
     * Resolve a staff-typed family key to the canonical stored key: if it denotes an <b>existing</b>
     * family (same {@link AccountIdNormalizer#compareKey}), reuse that family's exact spelling so
     * {@code "Gray"} / {@code "gray"} / {@code "Gray_Account"} all land on the one family; otherwise
     * mint a fresh {@link AccountIdNormalizer#canonical} key ({@code "Smith"} → {@code "Smith_Account"}).
     * Null/blank yields null. Mirrors the Brevo family-link sync's matching so manual and automatic
     * assignment agree on one key per family — the fix for typo-forked duplicate families.
     */
    private String resolveFamilyKey(String raw) {
        String compareKey = AccountIdNormalizer.compareKey(raw);
        if (compareKey.isEmpty()) {
            return null;
        }
        return existingKeysByCompareKey().getOrDefault(compareKey, AccountIdNormalizer.canonical(raw));
    }

    /**
     * compareKey → the exact stored family key currently in use, across both assigned roster students
     * and existing {@link FamilyAssociation} rows (so an emptied-but-not-yet-deleted family still
     * anchors its spelling). First spelling seen wins for a given compareKey.
     */
    private Map<String, String> existingKeysByCompareKey() {
        Map<String, String> byCompareKey = new LinkedHashMap<>();
        rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull().forEach(r -> {
            String key = r.getAccountId().trim();
            byCompareKey.putIfAbsent(AccountIdNormalizer.compareKey(key), key);
        });
        familyRepo.findAll().forEach(f -> {
            if (!isBlank(f.getAccountId())) {
                String key = f.getAccountId().trim();
                byCompareKey.putIfAbsent(AccountIdNormalizer.compareKey(key), key);
            }
        });
        return byCompareKey;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private FamilyAssociation getOrCreateFamily(String accountId) {
        return familyRepo.findById(accountId).orElseGet(() -> createFamily(accountId));
    }

    private FamilyAssociation createFamily(String accountId) {
        FamilyAssociation fam = new FamilyAssociation();
        fam.setAccountId(accountId);
        fam.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return familyRepo.save(fam);
    }

    /**
     * Move every assigned member (and the family metadata) from {@code oldKey} to {@code newKey}, then
     * delete the old family row. Members are matched case-insensitively on their stored key. The old
     * name/notes are carried onto the new row only where it doesn't already have its own, so merging
     * into an existing family never clobbers that family's details. Shared by rename and merge.
     */
    private void repointFamily(String oldKey, String newKey) {
        List<RosterStudent> members = rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull().stream()
                .filter(r -> oldKey.equalsIgnoreCase(r.getAccountId().trim()))
                .toList();
        members.forEach(r -> r.setAccountId(newKey));
        if (!members.isEmpty()) {
            rosterStudentRepo.saveAll(members);
        }

        FamilyAssociation oldFam = familyRepo.findById(oldKey).orElse(null);
        if (oldFam == null) {
            return;
        }
        FamilyAssociation newFam = getOrCreateFamily(newKey);
        if (isBlank(newFam.getName())) {
            newFam.setName(oldFam.getName());
        }
        if (isBlank(newFam.getNotes())) {
            newFam.setNotes(oldFam.getNotes());
        }
        newFam.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        familyRepo.save(newFam);
        familyRepo.delete(oldFam);
    }

    private static StudentView toView(RosterStudent s) {
        return new StudentView(s.getExtId(), s.getName(), s.getEmail(), s.getAccountId());
    }

    // ── DTOs carried to the view (scalar-only, safe with open-in-view off) ────────

    /** One family: the Account_ID key, its staff-set name/notes, and its assigned students. */
    public record FamilyView(String accountId, String name, String notes, List<StudentView> members) {
        public int size() { return members.size(); }

        /** Friendly name when set, otherwise the raw Account_ID key. */
        public String displayName() {
            return (name != null && !name.isBlank()) ? name : accountId;
        }
    }

    /** One roster student in the Association view. {@code extId} is its unique id (the assign key). */
    public record StudentView(String extId, String name, String email, String accountId) {}
}
