package ca.vicilearning.dashboard.rules;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MockTaskService {

    /**
     * Simulates the database rows that would be generated after a background sync.
     * We use your verified sandbox profile email to ensure safe, isolated testing.
     */
    public List<BrevoReviewTask> getSimulatedSyncTasks() {
        return List.of(
            new BrevoReviewTask(101L, "Miata Boy", "miataboy@maildrop.cc", "Lapsed: No sessions booked in 14 days", 1L),
            new BrevoReviewTask(102L, "John Doe", "johnboy100@maildrop.cc", "Payment Overdue: Invoice #2026-A ($150.00)", 2L)
        );
    }
}