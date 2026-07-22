package ca.vicilearning.dashboard.tutorportal;

import ca.vicilearning.dashboard.domain.StudentStatus;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.StudentRow;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.UpcomingSession;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Single source of dummy data for the whole tutor portal (overview, bookings, students)
 * Kept in one place on purpose so all three pages agree with each other
 */
@Component
public class TutorPortalSampleData {

    public String tutorName() {
        return "Priya Sharma";
    }

    public List<StudentRow> myStudents() {
        // basic contact info only, extId left null since its an internal ref tutors don't need to see
        return List.of(
                new StudentRow(1L, "Zoe Lam", "VICI-0012", null, "zoe.parent@example.com", "604-555-0142", 2, 2.0, StudentStatus.ACTIVE),
                new StudentRow(2L, "Henry Cohen", "VICI-0034", null, "henry.parent@example.com", "604-555-0198", 1, 1.0, StudentStatus.ACTIVE),
                new StudentRow(3L, "Amelia Diaz", "VICI-0051", null, "amelia.parent@example.com", "604-555-0176", 2, 3.0, StudentStatus.ACTIVE),
                new StudentRow(4L, "Samuel Cohen", "VICI-0022", null, "sam.parent@example.com", "604-555-0110", 1, 0.5, StudentStatus.ACTIVE),
                new StudentRow(5L, "Olivia Roy", "VICI-0067", null, "olivia.parent@example.com", "604-555-0165", 1, 1.5, StudentStatus.ACTIVE),
                new StudentRow(6L, "Daniel Gill", "VICI-0089", null, "daniel.parent@example.com", "604-555-0203", 1, 1.0, StudentStatus.ACTIVE)
        );
    }

    public List<UpcomingSession> myUpcoming() {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return List.of(
                new UpcomingSession("Zoe Lam", tutorName(), "Virtual 1hr Tutoring Session",
                        weekStart.plusDays(1).atTime(15, 0), "CONFIRMED"),
                new UpcomingSession("Henry Cohen", tutorName(), "In-Person 1hr Tutoring Session",
                        weekStart.plusDays(1).atTime(17, 0), "CONFIRMED"),
                new UpcomingSession("Amelia Diaz", tutorName(), "2hr Intensive Session",
                        weekStart.plusDays(3).atTime(13, 0), "PENDING"),
                new UpcomingSession("Samuel Cohen", tutorName(), "30min Tutoring Session",
                        weekStart.plusDays(4).atTime(16, 30), "CONFIRMED"),
                new UpcomingSession("Olivia Roy", tutorName(), "Virtual 1hr Tutoring Session",
                        weekStart.plusDays(0).atTime(11, 0), "CONFIRMED"),
                new UpcomingSession("Daniel Gill", tutorName(), "In-Person 1hr Tutoring Session",
                        weekStart.plusDays(2).atTime(14, 0), "CONFIRMED")
        );
    }

    public int sessionsThisWeek() {
        return myUpcoming().size();
    }

    public double hoursThisWeek() {
        return 9.5;
    }

    public int studentCount() {
        return myStudents().size();
    }
}