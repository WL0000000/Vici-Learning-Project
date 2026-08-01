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
     * {@link FamilyAssociation} row (created if the key is new). Blank input is a no-op so an empty
     * form submission can't wipe an assignment.
     */
    @Transactional
    public void assignToFamily(String extId, String accountId) {
        if (extId == null || extId.isBlank() || accountId == null || accountId.isBlank()) {
            return;
        }
        String key = accountId.trim();
        rosterStudentRepo.findById(extId).ifPresent(s -> {
            s.setAccountId(key);
            rosterStudentRepo.save(s);
        });
        getOrCreateFamily(key);
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
