package ca.vicilearning.dashboard.tutorportal;

import ca.vicilearning.dashboard.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorPortalDataServiceTest {

    @Mock TutorRepository tutorRepo;
    @Mock BookingRepository bookingRepo;
    @Mock RosterStudentRepository rosterStudentRepo;
    TutorPortalDataService service;

    @BeforeEach
    void setUp() {
        service = new TutorPortalDataService(tutorRepo, bookingRepo, rosterStudentRepo);
    }

    private Tutor makeTutor(long id, String name) {
        Tutor t = new Tutor();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private RosterStudent makeRoster(String extId, String name, String assignedTutor) {
        RosterStudent r = new RosterStudent();
        r.setExtId(extId);
        r.setName(name);
        r.setAssignedTutor(assignedTutor);
        return r;
    }

    private Booking makeBooking(String clientName, LocalDateTime startTime) {
        Student student = new Student();
        student.setName(clientName);
        Booking b = new Booking();
        b.setStudent(student);
        b.setStartTime(startTime);
        b.setStatus("confirmed");
        return b;
    }

    @Test
    void matchesBookingKeyedUnderParentNameWithAmpersandSeparatedKids() {
        // Real case found in production: SimplyBook books under "Julie Gray (Hannah & Kaylee)"
        // while Brevo's roster has the child's own name, "Hannah-Kaylee Gray".
        Tutor tutor = makeTutor(1L, "Mandeep Sangha");
        RosterStudent roster = makeRoster("E1", "Hannah-Kaylee Gray", "Mandeep Sangha");
        Booking booking = makeBooking("Julie Gray (Hannah & Kaylee)", LocalDateTime.now().minusDays(2));

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepo.findByTutorId(1L)).thenReturn(List.of(booking));

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).totalSessions()).isEqualTo(1);
    }

    @Test
    void matchesBookingKeyedUnderParentNameWithSlashSeparatedKids() {
        Tutor tutor = makeTutor(2L, "Mandeep Sangha");
        RosterStudent roster = makeRoster("E2", "Phoenix Ourn", "Mandeep Sangha");
        Booking booking = makeBooking("Jen Ourn (Phoenix / Natalie)", LocalDateTime.now().minusDays(1));

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepo.findByTutorId(2L)).thenReturn(List.of(booking));

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries.get(0).totalSessions()).isEqualTo(1);
    }

    @Test
    void matchesSingleNameInParenthesesWithNoSeparator() {
        Tutor tutor = makeTutor(3L, "Ashman Grewal");
        RosterStudent roster = makeRoster("E3", "Anna Smith", "Ashman Grewal");
        Booking booking = makeBooking("Laura (Anna)", LocalDateTime.now().minusDays(1));

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepo.findByTutorId(3L)).thenReturn(List.of(booking));

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries.get(0).totalSessions()).isEqualTo(1);
    }

    @Test
    void stillMatchesExactBookingNameEqualToStudentsOwnName() {
        Tutor tutor = makeTutor(4L, "Cypher Neri");
        RosterStudent roster = makeRoster("E4", "Marcus Chan", "Cypher Neri");
        Booking booking = makeBooking("Marcus Chan", LocalDateTime.now().minusDays(1));

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepo.findByTutorId(4L)).thenReturn(List.of(booking));

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries.get(0).totalSessions()).isEqualTo(1);
    }

    @Test
    void doesNotMatchUnrelatedFamilyBooking() {
        Tutor tutor = makeTutor(5L, "Jujhar Singh");
        RosterStudent roster = makeRoster("E5", "Naavya Sidhu", "Jujhar Singh");
        Booking unrelated = makeBooking("Some Other Family (Kid1 & Kid2)", LocalDateTime.now().minusDays(1));

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepo.findByTutorId(5L)).thenReturn(List.of(unrelated));

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries.get(0).totalSessions()).isEqualTo(0);
    }

    @Test
    void avgSessionsPerStudent_usesSameFuzzyMatchAsMyStudentSummaries() {
        Tutor tutor = makeTutor(6L, "Mandeep Sangha");
        RosterStudent roster = makeRoster("E6", "Hannah-Kaylee Gray", "Mandeep Sangha");
        Booking recent = makeBooking("Julie Gray (Hannah & Kaylee)", LocalDateTime.now().minusDays(2));

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepo.findByTutorId(6L)).thenReturn(List.of(recent));

        double avg = service.avgSessionsPerStudent(tutor, TutorPortalDataService.StatsPeriod.ALL_TIME);

        assertThat(avg).isEqualTo(1.0);
    }

    @Test
    void matchesAssignedTutorGivenAsFirstNameOnly() {
        // turns out staff often just type the tutor's first name into Brevo's ASSIGNED_TUTOR
        // field, "Ashman" instead of "Ashman Grewal"
        Tutor tutor = makeTutor(7L, "Ashman Grewal");
        RosterStudent roster = makeRoster("E7", "Claire Hinds", "Ashman");

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepo.findByTutorId(7L)).thenReturn(List.of());

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries).extracting(TutorPortalDataService.StudentSummary::name)
                .containsExactly("Claire Hinds");
    }

    @Test
    void matchesOneNameOutOfACommaSeparatedAssignedTutorList() {
        // real data has entries like "Sara, Ashman" for students covered by more than one tutor
        Tutor tutor = makeTutor(8L, "Ashman Grewal");
        RosterStudent roster = makeRoster("E8", "Jack Phan", "Sara, Ashman");

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepo.findByTutorId(8L)).thenReturn(List.of());

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries).extracting(TutorPortalDataService.StudentSummary::name)
                .containsExactly("Jack Phan");
    }

    @Test
    void doesNotMatchUnrelatedFirstName() {
        Tutor tutor = makeTutor(9L, "Ashman Grewal");
        RosterStudent roster = makeRoster("E9", "Some Kid", "Michael");

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries).isEmpty();
    }

    @Test
    void doesNotMatchBrokenBrevoMergeTagPlaceholder() {
        // "{{ deal.assigned tutor }}" is a broken Brevo automation on the client's side (an
        // unresolved template variable), not something matching logic should try to bridge.
        Tutor tutor = makeTutor(10L, "Ashman Grewal");
        RosterStudent roster = makeRoster("E10", "Some Kid", "{{ deal.assigned tutor }}");

        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(roster));

        List<TutorPortalDataService.StudentSummary> summaries = service.myStudentSummaries(tutor);

        assertThat(summaries).isEmpty();
    }
}
