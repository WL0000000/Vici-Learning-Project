package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByStartTimeBetween(LocalDateTime from, LocalDateTime to);

    List<Booking> findByStudentId(Long studentId);

    List<Booking> findByTutorId(Long tutorId);

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    List<Booking> findByDeletedAtIsNull();

    /**
     * Active (non-deleted) bookings whose start falls in [from, to), with student/tutor/service
     * eagerly fetched. The fetch joins let the metrics layer read those relations without an
     * open transaction (open-in-view is off) and avoid N+1 queries when aggregating.
     */
    @Query("""
            select b from Booking b
            join fetch b.student
            left join fetch b.tutor
            join fetch b.service
            where b.deletedAt is null
              and b.startTime >= :from and b.startTime < :to
            """)
    List<Booking> findActiveWithRefsBetween(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    /**
     * All active (non-deleted) bookings with student + service eagerly fetched — lets the metrics
     * layer read each booking's service category/location per student without an open transaction
     * (open-in-view is off). No date window: the family rollup summarises a family's whole history.
     */
    @Query("""
            select b from Booking b
            join fetch b.student
            join fetch b.service
            where b.deletedAt is null
            """)
    List<Booking> findActiveWithStudentAndService();
}
