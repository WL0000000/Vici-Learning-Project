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

    @Column(name = "parent_email", nullable = false)
    private String parentEmail; // NEW: Stored parent email for direct routing queries

    @Column(name = "lapsed_now", nullable = false)
    private boolean lapsedNow; // Dynamically calculated timeline state

    @Column(name = "lapsed_status", nullable = false)
    private boolean lapsedStatus; // Stored state fetched from Brevo attributes

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    public AlertStudent() {}

    public AlertStudent(String name, String accountId, String parentEmail, boolean lapsedNow, boolean lapsedStatus, LocalDateTime lastCheckedAt) {
        this.name = name;
        this.accountId = accountId;
        this.parentEmail = parentEmail;
        this.lapsedNow = lapsedNow;
        this.lapsedStatus = lapsedStatus;
        this.lastCheckedAt = lastCheckedAt;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getParentEmail() { return parentEmail; }
    public void setParentEmail(String parentEmail) { this.parentEmail = parentEmail; }

    public boolean isLapsedNow() { return lapsedNow; }
    public void setLapsedNow(boolean lapsedNow) { this.lapsedNow = lapsedNow; }

    public boolean isLapsedStatus() { return lapsedStatus; }
    public void setLapsedStatus(boolean lapsedStatus) { this.lapsedStatus = lapsedStatus; }

    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}