package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    java.util.List<Student> findByDeletedAtIsNull();

    // Active students that still need their Account_ID resolved from REST v2. Lets the sync
    // fetch only the unlinked backlog instead of every student on every run (avoids an N+1).
    // Doubles as the Association Account "unassigned" queue: no family assigned yet.
    java.util.List<Student> findByDeletedAtIsNullAndAccountIdIsNull();

    // Active students already assigned to a family (Account_ID set) — the source for the
    // Association Account family rollup.
    java.util.List<Student> findByDeletedAtIsNullAndAccountIdIsNotNull();

    // Active students whose EXT_ID (Brevo per-student id) isn't set yet — the backlog the sync
    // matches against Brevo contacts, so a steady-state run only touches newly-added students.
    java.util.List<Student> findByDeletedAtIsNullAndExtIdIsNull();
}
