package ca.vicilearning.dashboard.association;

import ca.vicilearning.dashboard.domain.FamilyAssociation;
import ca.vicilearning.dashboard.domain.FamilyAssociationRepository;
import ca.vicilearning.dashboard.domain.RosterStudent;
import ca.vicilearning.dashboard.domain.RosterStudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssociationServiceTest {

    @Mock
    private RosterStudentRepository rosterStudentRepo;
    @Mock
    private FamilyAssociationRepository familyRepo;

    @InjectMocks
    private AssociationService service;

    // ── #3: staff-typed key is normalized on assign ──────────────────────────────

    @Test
    void assign_mintsCanonicalKeyForANewFamily() {
        noExistingFamilies();
        RosterStudent r = roster("EXT-1", null);
        when(rosterStudentRepo.findById("EXT-1")).thenReturn(Optional.of(r));
        when(familyRepo.findById("Smith_Account")).thenReturn(Optional.empty());
        when(familyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assignToFamily("EXT-1", "Smith");

        // A bare surname becomes the canonical Surname_Account key.
        assertThat(r.getAccountId()).isEqualTo("Smith_Account");
        verify(rosterStudentRepo).save(r);
    }

    @Test
    void assign_reusesAnExistingFamilySpelling_insteadOfForking() {
        // A family already spelled "Gray_Account" exists (via an assigned sibling).
        RosterStudent sibling = roster("EXT-0", "Gray_Account");
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull()).thenReturn(List.of(sibling));
        when(familyRepo.findAll()).thenReturn(List.of());
        RosterStudent r = roster("EXT-2", null);
        when(rosterStudentRepo.findById("EXT-2")).thenReturn(Optional.of(r));
        when(familyRepo.findById("Gray_Account")).thenReturn(Optional.of(family("Gray_Account")));

        // Staff type "gray" — a different spelling of the same family.
        service.assignToFamily("EXT-2", "gray");

        // It folds into the existing family, not a new "Gray" / "gray_Account".
        assertThat(r.getAccountId()).isEqualTo("Gray_Account");
        verify(rosterStudentRepo).save(r);
    }

    @Test
    void assign_blankInputsAreNoOps() {
        service.assignToFamily("", "Gray");
        service.assignToFamily("EXT-1", "   ");
        verify(rosterStudentRepo, never()).save(any());
        verify(familyRepo, never()).save(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** No families exist yet — both lookups the resolver consults return empty. */
    private void noExistingFamilies() {
        lenient().when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull()).thenReturn(List.of());
        lenient().when(familyRepo.findAll()).thenReturn(List.of());
    }

    private static RosterStudent roster(String extId, String accountId) {
        RosterStudent r = new RosterStudent();
        r.setExtId(extId);
        r.setName(extId);
        r.setAccountId(accountId);
        return r;
    }

    private static FamilyAssociation family(String accountId) {
        FamilyAssociation f = new FamilyAssociation();
        f.setAccountId(accountId);
        return f;
    }
}
