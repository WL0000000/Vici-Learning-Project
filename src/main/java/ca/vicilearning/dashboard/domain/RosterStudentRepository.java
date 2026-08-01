package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RosterStudentRepository extends JpaRepository<RosterStudent, String> {

    // Present in Brevo (not soft-deleted).
    List<RosterStudent> findByDeletedAtIsNull();

    long countByDeletedAtIsNull();

    // The Association "unassigned" queue: no family assigned yet.
    List<RosterStudent> findByDeletedAtIsNullAndAccountIdIsNull();

    // Assigned to a family — the source for the Association family rollup.
    List<RosterStudent> findByDeletedAtIsNullAndAccountIdIsNotNull();
}
