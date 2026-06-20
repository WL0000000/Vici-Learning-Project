package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    // Active = not soft-deleted (still present in SimplyBook.me).
    long countByDeletedAtIsNull();

    java.util.List<Service> findByDeletedAtIsNull();
}
