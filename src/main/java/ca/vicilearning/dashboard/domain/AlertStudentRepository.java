package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AlertStudentRepository extends JpaRepository<AlertStudent, String> {
    
    // Groups alert snapshots belonging to a shared family container
    List<AlertStudent> findByAccountId(String accountId);

    // Clean JPQL lookup matching your streamlined property aliases
    @Query("SELECT a FROM AlertStudent a WHERE a.lapsedNow != a.lapsedStatus")
    List<AlertStudent> findDiscrepancies();
}