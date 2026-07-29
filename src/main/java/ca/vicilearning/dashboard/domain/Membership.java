package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A client membership from SimplyBook.me REST API v2 (JSON-RPC cannot return memberships).
 *
 * <p><b>Prepaid-credit model, CONFIRMED (Meeting #3 + live data 2026-07-23).</b> A family buys a
 * block of prepaid sessions on an annual term ({@code startDate} → {@code endDate}); each booking
 * decrements {@code remainingCount} (the export's {@code rest} column) and at 0 they can no longer
 * book. Some packages are {@code unlimited} instead (no countdown). Each membership links to an
 * {@code invoiceNumber} and carries its purchase {@code purchaseDate}; {@code recurring} marks
 * auto-renewing plans.
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

    // Remaining prepaid sessions (the export's `rest` column). null when the package is unlimited
    // or upstream exposes no countable balance — callers must treat null as "no countdown", not 0.
    private Integer remainingCount;

    // True for unlimited-style packages (no session countdown). null when upstream didn't state it.
    private Boolean unlimited;

    // Auto-renewing plan (SimplyBook is_recurring). null when upstream didn't state it.
    private Boolean recurring;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    // When the membership was purchased (the export's `Date` column). Distinct from startDate: the
    // term start. Nullable — not every shape carries it.
    private LocalDateTime purchaseDate;

    // The linked invoice's human-readable number (e.g. "SI-2026000096") — the "next invoice"
    // reference shown on the family view. Nullable.
    private String invoiceNumber;

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

    public Boolean getUnlimited() { return unlimited; }
    public void setUnlimited(Boolean unlimited) { this.unlimited = unlimited; }

    public Boolean getRecurring() { return recurring; }
    public void setRecurring(Boolean recurring) { this.recurring = recurring; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public LocalDateTime getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDateTime purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
