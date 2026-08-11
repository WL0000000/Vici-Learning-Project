package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;

@Entity
@Table(name = "tutors")
public class Tutor {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private String email;

    private String phone;

    @Column(nullable = false)
    private boolean active;

    // Active/Inactive/Legacy — see TutorStatus. Recomputed each sync (Active/Inactive) except
    // when already Legacy, which is a manual/sticky state the sync never overwrites.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'INACTIVE'")
    private TutorStatus status = TutorStatus.INACTIVE;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    // Soft-delete marker, no longer set for tutors (see TutorStatus.LEGACY, used instead when a
    // tutor disappears from SimplyBook.me so their record stays visible). Kept for schema/history
    // compatibility and because other soft-deletable entities still share this shape.
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public TutorStatus getStatus() { return status; }
    public void setStatus(TutorStatus status) { this.status = status; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
