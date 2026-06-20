package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "services")
public class Service {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private Integer durationMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    // Soft-delete marker: set when a sync no longer finds this service upstream.
    // null = still present in SimplyBook.me. Distinct from `active`, which mirrors
    // the upstream visibility flag.
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
