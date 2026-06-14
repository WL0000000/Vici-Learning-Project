package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByStartTimeBetween(LocalDateTime from, LocalDateTime to);

    List<Booking> findByStudentId(Long studentId);

    List<Booking> findByTutorId(Long tutorId);
}
