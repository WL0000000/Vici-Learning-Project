package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;

/**
 * The authoritative per-student roster record, sourced from <b>Brevo</b> (onsite finding 2026-07-30).
 * This is the real student — one Brevo contact whose {@code CONTACT_TYPE} is "Student" — keyed by its
 * unique <b>{@code EXT_ID}</b>. Distinct from {@link Student}, which despite its name is the
 * <b>SimplyBook client/account</b> that bookings, invoices and memberships hang off; SimplyBook has no
 * parent-child model and its client list is duplicated/stale, so it is not the student roster.
 *
 * <p><b>Why a separate entity:</b> a SimplyBook booking cannot be tied to an individual Brevo student
 * (Sara: "no way right away"), so the two live in separate tables and join only at the <b>family</b>
 * level via {@code accountId}. Identity, status and family assignment come from here; hours/sessions
 * come from {@link Student}/{@link Booking} and roll up to the family.
 *
 * <p>{@code accountId} (the family key) is <b>staff-owned</b> — Brevo has no usable family field, so
 * new students arrive unassigned ({@code accountId == null}) and staff assign them on the Association
 * page (Sara's #1 feature). The sync preserves a staff-set {@code accountId} across runs.
 *
 * <p>{@code assignedTutor} (added Meeting 5 follow-up) is the primary-tutor source of truth from
 * Brevo's ASSIGNED_TUTOR field — distinct from who taught any one SimplyBook session. A substitute
 * tutor covering a single booking should not make a student show up as "theirs" in the tutor portal;
 * only the Brevo-assigned primary tutor should.
 */
@Entity
@Table(name = "roster_students")
public class RosterStudent {

    /** The Brevo EXT_ID — the unique per-student key. */
    @Id
    private String extId;

    @Column(nullable = false)
    private String name;

    // Optional in Brevo — some students have neither email nor phone (so email is never a join key).
    private String email;

    private String phone;

    // Enrolment status from Brevo CONTACT_STATUS. Non-null, defaults ACTIVE. @ColumnDefault so the
    // column applies cleanly on any populated table (see the schema-change convention).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'ACTIVE'")
    private StudentStatus status = StudentStatus.ACTIVE;

    // Family / association key (Surname_Account), staff-assigned via the Association page. Nullable =
    // unassigned (awaits staff assignment). Matches Student.accountId so families join across systems.
    private String accountId;

    // Brevo numeric contact id, kept so a family (a Brevo Company) can be matched to its student
    // contacts by id (never email). Used to bootstrap the family for still-unassigned students.
    private Long brevoContactId;

    // Primary tutor assignment from Brevo's ASSIGNED_TUTOR field on the student contact. This is
    // the source of truth for "whose student is this" — distinct from who actually taught any one
    // session (a substitute tutor's booking shouldn't make a student "theirs"). Nullable: some
    // students may not have an assigned tutor set yet in Brevo.
    private String assignedTutor;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    // Soft-delete marker: set when a sync no longer finds this student in Brevo. null = still present.
    private LocalDateTime deletedAt;

    public String getExtId() { return extId; }
    public void setExtId(String extId) { this.extId = extId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public StudentStatus getStatus() { return status; }
    public void setStatus(StudentStatus status) { this.status = status; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public Long getBrevoContactId() { return brevoContactId; }
    public void setBrevoContactId(Long brevoContactId) { this.brevoContactId = brevoContactId; }

    public String getAssignedTutor() { return assignedTutor; }
    public void setAssignedTutor(String assignedTutor) { this.assignedTutor = assignedTutor; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}