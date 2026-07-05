package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    List<Membership> findByDeletedAtIsNull();

    List<Membership> findByStudentIdAndDeletedAtIsNull(Long studentId);

    // Active memberships at/below a balance threshold — the "running low / can't book at 0"
    // families the rules engine will want to surface.
    List<Membership> findByActiveTrueAndDeletedAtIsNullAndRemainingCountLessThanEqual(int threshold);
}
