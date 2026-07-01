package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/**
 * Data Access Object managing persistence interactions for anomalous {@link AlertStudent} configurations.
 */
public interface AlertStudentRepository extends JpaRepository<AlertStudent, String> {
    
    /**
     * Resolves all student entity entries belonging to a shared system account infrastructure context.
     *
     * @param accountId Shared container grouping target string.
     * @return Structured collection matching criteria filter.
     */
    List<AlertStudent> findByAccountId(String accountId);

    /**
     * Identifies discrepancies between the calculated booking timelines and active Brevo parameters.
     *
     * @return Stream list tracking sync conflicts.
     */
    @Query("SELECT a FROM AlertStudent a WHERE a.lapsedNow != a.lapsedStatus")
    List<AlertStudent> findDiscrepancies();
}