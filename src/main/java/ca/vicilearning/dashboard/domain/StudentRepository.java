package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    java.util.List<Student> findByDeletedAtIsNull();

    // Active students that still need their Account_ID resolved from REST v2. Lets the sync
    // fetch only the unlinked backlog instead of every student on every run (avoids an N+1).
    java.util.List<Student> findByDeletedAtIsNullAndAccountIdIsNull();
}
