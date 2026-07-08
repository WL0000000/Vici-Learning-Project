package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    List<Invoice> findByDeletedAtIsNull();

    /**
     * Active (non-deleted) invoices with the linked student eagerly fetched, so the metrics
     * layer can read {@code invoice.student.name} without an open transaction (open-in-view is
     * off). Left join: the student link is optional (invoices for untracked clients are kept).
     */
    @Query("""
            select i from Invoice i
            left join fetch i.student
            where i.deletedAt is null
            """)
    List<Invoice> findActiveWithStudent();

    // Unpaid invoices for the rules engine's "unpaid families" flag. Case-insensitive on
    // status so "Paid"/"paid" upstream variations don't slip through.
    List<Invoice> findByStatusIgnoreCaseNotAndDeletedAtIsNull(String status);

    List<Invoice> findByStudentIdAndDeletedAtIsNull(Long studentId);
}
