package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A client membership from SimplyBook.me REST API v2 (JSON-RPC cannot return memberships).
 *
 * <p><b>Model unconfirmed.</b> This was originally built assuming a pre-paid "credit" model
 * where {@code remainingCount} is the balance of sessions a family has left (the "can't book at
 * 0" idea). The client has since indicated membership may instead be a plain active/inactive
 * plan — possibly with a renewal/expiry date — and no session countdown. Until that's confirmed
 * with Sara, treat {@code remainingCount} as optional/provisional: the {@code active} flag plus
 * {@code startDate}/{@code endDate} already support a status-or-renewal model with no schema
 * change. Do not build balance-alerting UI on {@code remainingCount} until the model is confirmed.
 *
 * <p>Linked to a {@link Student} by SimplyBook client id when available; the link is optional
 * for the same reason as {@link Invoice} — we keep the record even for untracked clients.
 */
@Entity
@Table(name = "memberships")
public class Membership {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    private String name;

    @Column(nullable = false)
    private boolean active;

    // Remaining pre-paid sessions/visits under the (unconfirmed) credit model. null when
    // upstream exposes no countable balance — which may be always, if membership is really a
    // status/renewal plan. Kept and still parsed defensively pending confirmation from Sara.
    private Integer remainingCount;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    // Soft-delete marker: set when a sync no longer finds this membership upstream.
    // null = still present in SimplyBook.me.
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getRemainingCount() { return remainingCount; }
    public void setRemainingCount(Integer remainingCount) { this.remainingCount = remainingCount; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
