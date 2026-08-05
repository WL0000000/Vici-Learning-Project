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

import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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

    // ── #1: move an already-assigned student to another family ───────────────────

    @Test
    void assign_movesAnAlreadyAssignedStudentToAnotherFamily() {
        // Two families already exist; EXT-9 currently belongs to Gray.
        RosterStudent gray = roster("EXT-9", "Gray_Account");
        RosterStudent leeSibling = roster("EXT-8", "Lee_Account");
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull())
                .thenReturn(List.of(gray, leeSibling));
        when(familyRepo.findAll()).thenReturn(List.of());
        when(rosterStudentRepo.findById("EXT-9")).thenReturn(Optional.of(gray));
        when(familyRepo.findById("Lee_Account")).thenReturn(Optional.of(family("Lee_Account")));

        service.assignToFamily("EXT-9", "Lee");

        assertThat(gray.getAccountId()).isEqualTo("Lee_Account");
        verify(rosterStudentRepo).save(gray);
    }

    // ── #2a: unassign a student back to the queue ────────────────────────────────

    @Test
    void unassign_clearsTheFamilyKey() {
        RosterStudent r = roster("EXT-5", "Gray_Account");
        when(rosterStudentRepo.findById("EXT-5")).thenReturn(Optional.of(r));

        service.unassign("EXT-5");

        assertThat(r.getAccountId()).isNull();
        verify(rosterStudentRepo).save(r);
    }

    @Test
    void unassign_blankIdIsANoOp() {
        service.unassign("  ");
        verify(rosterStudentRepo, never()).save(any());
    }

    // ── #2b: rename a family key ─────────────────────────────────────────────────

    @Test
    void rename_repointsEveryMemberAndMigratesMetadata() {
        RosterStudent m1 = roster("EXT-1", "Gray_Account");
        RosterStudent m2 = roster("EXT-2", "Gray_Account");
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull()).thenReturn(List.of(m1, m2));
        when(familyRepo.findAll()).thenReturn(List.of());
        FamilyAssociation oldFam = family("Gray_Account");
        oldFam.setName("Gray Family");
        oldFam.setNotes("VIP");
        when(familyRepo.findById("Gray_Account")).thenReturn(Optional.of(oldFam));
        when(familyRepo.findById("Grey_Account")).thenReturn(Optional.empty());
        when(familyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.renameFamily("Gray_Account", "Grey");

        // Every member now points at the new key…
        assertThat(m1.getAccountId()).isEqualTo("Grey_Account");
        assertThat(m2.getAccountId()).isEqualTo("Grey_Account");
        verify(rosterStudentRepo).saveAll(any());
        // …the name/notes are carried onto the new family row…
        ArgumentCaptor<FamilyAssociation> saved = ArgumentCaptor.forClass(FamilyAssociation.class);
        verify(familyRepo, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).anySatisfy(f -> {
            assertThat(f.getAccountId()).isEqualTo("Grey_Account");
            assertThat(f.getName()).isEqualTo("Gray Family");
            assertThat(f.getNotes()).isEqualTo("VIP");
        });
        // …and the old row is removed.
        verify(familyRepo).delete(oldFam);
    }

    @Test
    void rename_toTheSameFamilyIsANoOp() {
        RosterStudent m = roster("EXT-1", "Gray_Account");
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull()).thenReturn(List.of(m));
        when(familyRepo.findAll()).thenReturn(List.of());

        // "gray" resolves back to the existing "Gray_Account" — same family, nothing to do.
        service.renameFamily("Gray_Account", "gray");

        assertThat(m.getAccountId()).isEqualTo("Gray_Account");
        verify(rosterStudentRepo, never()).saveAll(any());
        verify(familyRepo, never()).delete(any());
    }

    // ── #2c: merge one family into another ───────────────────────────────────────

    @Test
    void merge_movesSourceMembersIntoTargetAndDeletesSource() {
        RosterStudent fromMember = roster("EXT-1", "Gray_Account");
        RosterStudent targetMember = roster("EXT-2", "Grayy_Account");
        when(familyRepo.findById("Grayy_Account")).thenReturn(Optional.of(family("Grayy_Account")));
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull())
                .thenReturn(List.of(fromMember, targetMember));
        FamilyAssociation fromFam = family("Gray_Account");
        when(familyRepo.findById("Gray_Account")).thenReturn(Optional.of(fromFam));
        when(familyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.mergeFamilies("Gray_Account", "Grayy_Account");

        assertThat(fromMember.getAccountId()).isEqualTo("Grayy_Account"); // moved
        assertThat(targetMember.getAccountId()).isEqualTo("Grayy_Account"); // untouched
        verify(familyRepo).delete(fromFam);
    }

    @Test
    void merge_noOpWhenTargetDoesNotExist() {
        when(familyRepo.findById("Ghost_Account")).thenReturn(Optional.empty());

        service.mergeFamilies("Gray_Account", "Ghost_Account");

        verify(rosterStudentRepo, never()).saveAll(any());
        verify(familyRepo, never()).delete(any());
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
