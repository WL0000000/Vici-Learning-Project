package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    List<Membership> findByDeletedAtIsNull();

    List<Membership> findByStudentIdAndDeletedAtIsNull(Long studentId);

    // Active memberships at/below a balance threshold — the "running low / can't book at 0"
    // families. The credit model is CONFIRMED (Meeting #3), so this alerting is valid;
    // DashboardMetricsService.actionRequired() surfaces it (via its own per-student map so it can
    // read the student name without the lazy Membership.student proxy).
    List<Membership> findByActiveTrueAndDeletedAtIsNullAndRemainingCountLessThanEqual(int threshold);
}
