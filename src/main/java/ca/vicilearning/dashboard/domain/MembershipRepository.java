package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    List<Membership> findByDeletedAtIsNull();

    List<Membership> findByStudentIdAndDeletedAtIsNull(Long studentId);

    // Active memberships at/below a balance threshold — the "running low / can't book at 0"
    // families a rules engine could surface. PROVISIONAL and currently unused: it assumes the
    // credit model, which the client says may no longer apply. Do not wire into alerting until
    // the membership model is confirmed (see Membership javadoc).
    List<Membership> findByActiveTrueAndDeletedAtIsNullAndRemainingCountLessThanEqual(int threshold);
}
