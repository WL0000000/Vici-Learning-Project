package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Tracks a mismatch between our local lapse computation and what Brevo says, so it can be
// reviewed and resolved from the Automations queue.
@Entity
@Table(name = "alert_student")
public class AlertStudent {

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "lapsed_now", nullable = false)
    private boolean lapsedNow; // computed from local booking history

    @Column(name = "lapsed_status", nullable = false)
    private boolean lapsedStatus; // what Brevo's roster status says

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    // Copied from Student.email at sync time. Used to be resolved through a live Brevo lookup
    // keyed on a VICI_ACCOUNT_ID contact attribute, but that attribute doesn't seem to actually
    // exist on the real account, so every row just showed "not found". Student.email is already
    // sitting there locally, no reason to round-trip through Brevo for it.
    @Column(name = "email")
    private String email;

    public AlertStudent() {}

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

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}