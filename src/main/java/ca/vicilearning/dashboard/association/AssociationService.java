package ca.vicilearning.dashboard.association;

import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Association Account feature (Sara's #1 ask, Meeting #3): the dashboard is the authoritative
 * family ↔ student map, since Brevo's family "Company" association doesn't export cleanly.
 *
 * <p>Model in this first scaffold: a student's <b>family</b> is its {@code accountId} (the
 * {@code Surname_Account} key, shared by siblings); its own unique id is {@code extId} (from
 * Brevo). A student with no {@code accountId} is <b>unassigned</b> and waits in a queue for staff
 * to assign it to a family. Assignment just sets {@code accountId}; grouping by that key yields the
 * families.
 *
 * <p>TODO (beyond scaffold): move the family off a bare string onto a first-class
 * {@code FamilyAssociation} entity (name, notes, canonical Brevo company id); sync {@code extId}
 * from Brevo; and let staff create/rename families rather than only reusing existing keys.
 */
@Service
public class AssociationService {

    private final StudentRepository studentRepo;

    public AssociationService(StudentRepository studentRepo) {
        this.studentRepo = studentRepo;
    }

    /** Students not yet assigned to a family (no Account_ID) — the assignment queue. */
    public List<StudentView> unassignedStudents() {
        return studentRepo.findByDeletedAtIsNullAndAccountIdIsNull().stream()
                .sorted(Comparator.comparing(Student::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(AssociationService::toView)
                .toList();
    }

    /** Assigned students rolled up by family (Account_ID), families sorted by key, members by name. */
    public List<FamilyView> families() {
        Map<String, List<StudentView>> byAccount = new LinkedHashMap<>();
        studentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull().stream()
                .sorted(Comparator.comparing(Student::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .forEach(s -> byAccount.computeIfAbsent(s.getAccountId().trim(), k -> new ArrayList<>())
                        .add(toView(s)));

        return byAccount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(e -> new FamilyView(e.getKey(), e.getValue()))
                .toList();
    }

    /** Distinct existing family keys, for populating the assignment dropdown. */
    public List<String> existingFamilyKeys() {
        return families().stream().map(FamilyView::accountId).toList();
    }

    /**
     * Assign a student to a family by setting its Account_ID. Blank input is treated as a no-op so
     * an empty form submission can't wipe an assignment. (Scaffold: no validation that the family
     * key exists — staff may type a new one to create a family.)
     */
    @Transactional
    public void assignToFamily(Long studentId, String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        studentRepo.findById(studentId).ifPresent(s -> {
            s.setAccountId(accountId.trim());
            studentRepo.save(s);
        });
    }

    private static StudentView toView(Student s) {
        return new StudentView(s.getId(), s.getName(), s.getExtId(), s.getEmail(), s.getAccountId());
    }

    // ── DTOs carried to the view (scalar-only, safe with open-in-view off) ────────

    /** One family: the Account_ID key and its assigned students. */
    public record FamilyView(String accountId, List<StudentView> members) {
        public int size() { return members.size(); }
    }

    public record StudentView(Long id, String name, String extId, String email, String accountId) {}
}
