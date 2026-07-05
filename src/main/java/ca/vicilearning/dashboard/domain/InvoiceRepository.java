package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    List<Invoice> findByDeletedAtIsNull();

    // Unpaid invoices for the rules engine's "unpaid families" flag. Case-insensitive on
    // status so "Paid"/"paid" upstream variations don't slip through.
    List<Invoice> findByStatusIgnoreCaseNotAndDeletedAtIsNull(String status);

    List<Invoice> findByStudentIdAndDeletedAtIsNull(Long studentId);
}
