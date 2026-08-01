package ca.vicilearning.dashboard.student;

import ca.vicilearning.dashboard.domain.RosterStudent;
import ca.vicilearning.dashboard.domain.RosterStudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentStatusServiceTest {

    @Mock
    private RosterStudentRepository rosterStudentRepo;

    @InjectMocks
    private StudentStatusService service;

    @Test
    void setsStatusOnKnownStudent() {
        RosterStudent r = new RosterStudent();
        r.setExtId("EXT-1");
        r.setStatus(StudentStatus.ACTIVE);
        when(rosterStudentRepo.findById("EXT-1")).thenReturn(Optional.of(r));

        assertThat(service.setStatus("EXT-1", "PAUSED")).isTrue();
        assertThat(r.getStatus()).isEqualTo(StudentStatus.PAUSED);
        verify(rosterStudentRepo).save(r);
    }

    @Test
    void acceptsTheFourValuesToleratingCase() {
        RosterStudent r = new RosterStudent();
        when(rosterStudentRepo.findById("EXT-2")).thenReturn(Optional.of(r));

        assertThat(service.setStatus("EXT-2", "Dropped")).isTrue();   // tolerant parse
        assertThat(r.getStatus()).isEqualTo(StudentStatus.DROPPED);
    }

    @Test
    void noOpWhenStudentUnknown() {
        when(rosterStudentRepo.findById("X")).thenReturn(Optional.empty());

        assertThat(service.setStatus("X", "PAUSED")).isFalse();
        verify(rosterStudentRepo, never()).save(any());
    }

    @Test
    void noOpForUnrecognizedStatus() {
        assertThat(service.setStatus("EXT-1", "BOGUS")).isFalse();
        verifyNoInteractions(rosterStudentRepo);
    }

    @Test
    void noOpForBlankIdOrNullStatus() {
        assertThat(service.setStatus("", "PAUSED")).isFalse();
        assertThat(service.setStatus("EXT-1", null)).isFalse();
        verifyNoInteractions(rosterStudentRepo);
    }
}
