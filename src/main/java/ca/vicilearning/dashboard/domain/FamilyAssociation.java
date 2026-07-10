package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A family / association account — the first-class record behind the Association Account feature
 * (Sara's #1 ask, Meeting #3). A family groups the students (siblings) that share an
 * {@code Account_ID}; this entity lets staff attach a friendly name, notes, and the canonical
 * Brevo company link to that family instead of it being just a bare string on each student.
 *
 * <p>Keyed by {@code accountId} — the {@code Surname_Account} string that also lives on
 * {@link Student#getAccountId()}, so the two join directly (no surrogate id needed). Unlike the
 * synced entities this is <b>locally owned</b> data (staff-maintained), so it has no
 * {@code deletedAt}/sync-reconciliation column.
 */
@Entity
@Table(name = "family_associations")
public class FamilyAssociation {

    // The Account_ID (Surname_Account) — matches Student.accountId. Natural key for the family.
    @Id
    private String accountId;

    // Friendly family name staff can set (e.g. "Gray Family"). Nullable until they name it.
    private String name;

    @Column(columnDefinition = "text")
    private String notes;

    // The canonical Brevo Company/association id, once we link it. Nullable for now.
    private String brevoCompanyId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getBrevoCompanyId() { return brevoCompanyId; }
    public void setBrevoCompanyId(String brevoCompanyId) { this.brevoCompanyId = brevoCompanyId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
