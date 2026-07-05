package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A client membership from SimplyBook.me REST API v2 (JSON-RPC cannot return memberships).
 * Behind the "can't book at 0" model: {@code remainingCount} is the balance of pre-paid
 * sessions a family has left, which the rules layer can use to flag families running low.
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

    // Remaining pre-paid sessions/visits on this membership. null when upstream doesn't
    // expose a countable balance (e.g. an unlimited membership).
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
