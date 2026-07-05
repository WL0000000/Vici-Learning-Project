package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A SimplyBook.me invoice/order, pulled from REST API v2 (JSON-RPC cannot return invoices).
 * Backs the "unpaid families" rule and the pending-invoices / cash-flow overview.
 *
 * <p>Linked to a {@link Student} by SimplyBook client id when we have that student locally.
 * The link is optional: an invoice for a client we don't track (e.g. since removed) is still
 * kept rather than dropped, so financial history is never silently lost.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private Long id;

    // Nullable on purpose: keep the invoice even if its client isn't in our students table.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    private String number;

    // Upstream status, lower-cased (e.g. "paid", "pending", "cancelled"). See isPaid().
    private String status;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private String currency;

    // When the invoice was issued upstream.
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    // Soft-delete marker: set when a sync no longer finds this invoice upstream.
    // null = still present in SimplyBook.me.
    private LocalDateTime deletedAt;

    /** True when the upstream status marks this invoice as settled. */
    public boolean isPaid() {
        return "paid".equalsIgnoreCase(status);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
