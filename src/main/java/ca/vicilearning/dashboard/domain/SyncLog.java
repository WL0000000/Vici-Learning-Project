package ca.vicilearning.dashboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_logs")
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private boolean success;

    private int studentsUpserted;
    private int tutorsUpserted;
    private int servicesUpserted;
    private int bookingsUpserted;

    private int studentsRemoved;
    private int tutorsRemoved;
    private int servicesRemoved;
    private int bookingsRemoved;

    // Count of students whose Account_ID (Brevo link) was set/updated from REST v2 this run.
    private int accountIdsLinked;

    @Column(columnDefinition = "text")
    private String errorMessage;

    public Long getId() { return id; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public int getStudentsUpserted() { return studentsUpserted; }
    public void setStudentsUpserted(int studentsUpserted) { this.studentsUpserted = studentsUpserted; }

    public int getTutorsUpserted() { return tutorsUpserted; }
    public void setTutorsUpserted(int tutorsUpserted) { this.tutorsUpserted = tutorsUpserted; }

    public int getServicesUpserted() { return servicesUpserted; }
    public void setServicesUpserted(int servicesUpserted) { this.servicesUpserted = servicesUpserted; }

    public int getBookingsUpserted() { return bookingsUpserted; }
    public void setBookingsUpserted(int bookingsUpserted) { this.bookingsUpserted = bookingsUpserted; }

    public int getStudentsRemoved() { return studentsRemoved; }
    public void setStudentsRemoved(int studentsRemoved) { this.studentsRemoved = studentsRemoved; }

    public int getTutorsRemoved() { return tutorsRemoved; }
    public void setTutorsRemoved(int tutorsRemoved) { this.tutorsRemoved = tutorsRemoved; }

    public int getServicesRemoved() { return servicesRemoved; }
    public void setServicesRemoved(int servicesRemoved) { this.servicesRemoved = servicesRemoved; }

    public int getBookingsRemoved() { return bookingsRemoved; }
    public void setBookingsRemoved(int bookingsRemoved) { this.bookingsRemoved = bookingsRemoved; }

    public int getAccountIdsLinked() { return accountIdsLinked; }
    public void setAccountIdsLinked(int accountIdsLinked) { this.accountIdsLinked = accountIdsLinked; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
