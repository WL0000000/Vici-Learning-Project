package ca.vicilearning.dashboard.rules;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Generates decoupled simulation data sets representing operational system alerts.
 * Swaps out cleanly once real entity-relation tables are finalized in production[cite: 70].
 */
@Service
public class MockTaskService {

    public List<BrevoReviewTask> getSimulatedSyncTasks() {
        return List.of(
            // Scenario A: Multi-Child tracking with parallel timelines [cite: 287]
            new BrevoReviewTask(
                101L, 
                "Miata Parent", 
                "miataboy100@gmail.com", // Linked to your valid sandbox context row [cite: 78]
                "VICI-OVK301",
                "Lapsed: Multiple children missing active calendar blocks", 
                1L,
                "Good Standing",
                List.of("Miata Boy", "Miata Girl"),
                List.of(LocalDate.of(2026, 5, 24), LocalDate.of(2026, 6, 6))
            ),
            // Scenario B: Single-Child tracking evaluating billing exception attributes [cite: 287]
            new BrevoReviewTask(
                102L, 
                "John Doe", 
                "johnboy100@maildrop.cc", 
                "VICI-OVK998",
                "Payment Overdue: System account status set to OVERDUE", 
                2L,
                "OVERDUE",
                List.of("Johnny Kid"),
                List.of(LocalDate.of(2026, 6, 20))
            )
        );
    }
}