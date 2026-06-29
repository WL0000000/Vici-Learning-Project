package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_student")
public class AlertStudent {

    @Id
    @Column(name = "name", nullable = false)
    private String name; // Student's full name (Primary Key)

    @Column(name = "account_id", nullable = false)
    private String accountId; // Grouping identifier (e.g., VICI-0001)

    @Column(name = "lapsed_now", nullable = false)
    private boolean lapsedNow; // Dynamically calculated timeline state

    @Column(name = "lapsed_status", nullable = false)
    private boolean lapsedStatus; // Managed in view engine mapping

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    public AlertStudent() {}

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public boolean isLapsedNow() { return lapsedNow; }
    public void setLapsedNow(boolean lapsedNow) { this.lapsedNow = lapsedNow; }

    public boolean isLapsedStatus() { return lapsedStatus; }
    public void setLapsedStatus(boolean lapsedStatus) { this.lapsedStatus = lapsedStatus; }

    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}