package ca.vicilearning.dashboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link FamilyAssociation}. Keyed by {@code accountId} (the Surname_Account
 * string), matching {@link Student#getAccountId()} so family metadata joins to its students.
 */
public interface FamilyAssociationRepository extends JpaRepository<FamilyAssociation, String> {
}
