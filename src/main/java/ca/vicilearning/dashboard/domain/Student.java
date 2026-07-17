package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "students")
public class Student {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private String email;

    private String phone;

    // The SimplyBook.me "Account_ID" custom client field — the key that links this
    // student/family to its canonical record in Brevo. Pulled via REST API v2 (JSON-RPC
    // cannot read custom fields). Nullable: not every client has it set upstream.
    //
    // Per Meeting #3, Account_ID is the FAMILY / association id (a Surname_Account string),
    // shared by siblings. It doubles as the assignable "family" key in the Association Account
    // feature: a student with accountId == null is unassigned and awaits staff assignment.
    private String accountId;

    // The Brevo "EXT_ID" — the unique identifier for THIS individual student (one student =
    // one Brevo contact). This is the only per-student unique id Vici actually uses. Distinct
    // from accountId, which is the family. Nullable until synced from Brevo. (Meeting #3.)
    private String extId;

    // Enrolment status — a manual ACTIVE/PAUSED flag (Meeting #4), stored as text so the value is
    // readable in the DB. Defaults to ACTIVE and is non-null; SimplyBook doesn't carry it, so the
    // sync preserves it across upserts (see SyncService.syncStudents). Distinct from "lapsed",
    // which is computed from booking recency.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudentStatus status = StudentStatus.ACTIVE;

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    // Soft-delete marker: set when a sync no longer finds this student upstream.
    // null = still present in SimplyBook.me.
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getExtId() { return extId; }
    public void setExtId(String extId) { this.extId = extId; }

    public StudentStatus getStatus() { return status; }
    public void setStatus(StudentStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
