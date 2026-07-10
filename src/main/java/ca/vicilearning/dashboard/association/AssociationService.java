package ca.vicilearning.dashboard.association;

import ca.vicilearning.dashboard.domain.FamilyAssociation;
import ca.vicilearning.dashboard.domain.FamilyAssociationRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
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
 * The Association Account feature (Sara's #1 ask, Meeting #3): the dashboard is the authoritative
 * family ↔ student map, since Brevo's family "Company" association doesn't export cleanly.
 *
 * <p>A student's <b>family</b> is its {@code accountId} (the {@code Surname_Account} key, shared by
 * siblings); its own unique id is {@code extId} (from Brevo). A student with no {@code accountId} is
 * <b>unassigned</b> and waits in a queue for staff to assign it. Each family also has a first-class
 * {@link FamilyAssociation} row (name, notes, Brevo company link), auto-created the first time the
 * family is seen — so grouping students by the key and attaching that row yields the families.
 *
 * <p>TODO (future): sync the Brevo company id onto the family; let staff merge/rename families.
 */
@Service
public class AssociationService {

    private final StudentRepository studentRepo;
    private final FamilyAssociationRepository familyRepo;

    public AssociationService(StudentRepository studentRepo, FamilyAssociationRepository familyRepo) {
        this.studentRepo = studentRepo;
        this.familyRepo = familyRepo;
    }

    /** Students not yet assigned to a family (no Account_ID) — the assignment queue. */
    public List<StudentView> unassignedStudents() {
        return studentRepo.findByDeletedAtIsNullAndAccountIdIsNull().stream()
                .sorted(Comparator.comparing(Student::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(AssociationService::toView)
                .toList();
    }

    /**
     * Assigned students rolled up by family (Account_ID), families sorted by key, members by name.
     * Each family carries its {@link FamilyAssociation} name/notes; a family seen for the first time
     * gets its row auto-created here (idempotent get-or-create), so existing seeded/synced families
     * are backfilled on first view. Transactional because of that create.
     */
    @Transactional
    public List<FamilyView> families() {
        Map<String, List<StudentView>> byAccount = new LinkedHashMap<>();
        studentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull().stream()
                .sorted(Comparator.comparing(Student::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
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
     * Assign a student to a family by setting its Account_ID, and ensure that family has a
     * {@link FamilyAssociation} row (created if the key is new). Blank input is a no-op so an empty
     * form submission can't wipe an assignment.
     */
    @Transactional
    public void assignToFamily(Long studentId, String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        String key = accountId.trim();
        studentRepo.findById(studentId).ifPresent(s -> {
            s.setAccountId(key);
            studentRepo.save(s);
        });
        getOrCreateFamily(key);
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

    private static StudentView toView(Student s) {
        return new StudentView(s.getId(), s.getName(), s.getExtId(), s.getEmail(), s.getAccountId());
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

    public record StudentView(Long id, String name, String extId, String email, String accountId) {}
}
