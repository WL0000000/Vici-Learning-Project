package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // same "running low" set but with the student eagerly fetched, for the Automations renewal
    // queue which needs contact info outside an open Hibernate session (same deal as
    // InvoiceRepository.findActiveWithStudent())
    @Query("""
            select m from Membership m
            join fetch m.student
            where m.active = true and m.deletedAt is null
              and m.remainingCount is not null and m.remainingCount <= :threshold
            """)
    List<Membership> findRunningLowWithStudent(@Param("threshold") int threshold);
}
