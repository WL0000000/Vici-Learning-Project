package ca.vicilearning.dashboard.student;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentStatusServiceTest {

    @Mock
    private StudentRepository studentRepo;

    @Mock
    private BrevoCommunicationService brevo;

    @InjectMocks
    private StudentStatusService service;

    @Test
    void setsStatusLocallyAndPushesToBrevo() {
        Student s = new Student();
        s.setStatus(StudentStatus.ACTIVE);
        s.setEmail("kid@example.com");
        when(studentRepo.findById(5L)).thenReturn(Optional.of(s));

        boolean updated = service.setStatus(5L, "PAUSED");

        assertThat(updated).isTrue();
        assertThat(s.getStatus()).isEqualTo(StudentStatus.PAUSED);
        verify(studentRepo).save(s);
        verify(brevo).updateContactAttributes("kid@example.com", Map.of("STUDENT_STATUS", "PAUSED"));
    }

    @Test
    void acceptsLowercaseAndTrimsWhitespace() {
        Student s = new Student();
        s.setEmail("kid@example.com");
        when(studentRepo.findById(1L)).thenReturn(Optional.of(s));

        assertThat(service.setStatus(1L, "  paused ")).isTrue();
        assertThat(s.getStatus()).isEqualTo(StudentStatus.PAUSED);
        verify(brevo).updateContactAttributes(eq("kid@example.com"), any());
    }

    @Test
    void setsLocallyButSkipsBrevoWhenStudentHasNoEmail() {
        Student s = new Student();
        // no email → nothing to key the Brevo contact on
        when(studentRepo.findById(2L)).thenReturn(Optional.of(s));

        assertThat(service.setStatus(2L, "PAUSED")).isTrue();
        assertThat(s.getStatus()).isEqualTo(StudentStatus.PAUSED);
        verify(studentRepo).save(s);
        verify(brevo, never()).updateContactAttributes(any(), any());
    }

    @Test
    void noOpWhenStudentUnknown() {
        when(studentRepo.findById(9L)).thenReturn(Optional.empty());

        assertThat(service.setStatus(9L, "PAUSED")).isFalse();
        verify(studentRepo, never()).save(any());
        verify(brevo, never()).updateContactAttributes(any(), any());
    }

    @Test
    void noOpForUnrecognizedStatus() {
        // Guards against a malformed request 500ing or blanking the column — never touches DB/Brevo.
        assertThat(service.setStatus(5L, "BOGUS")).isFalse();
        verifyNoInteractions(studentRepo, brevo);
    }

    @Test
    void noOpForNullStatus() {
        assertThat(service.setStatus(5L, null)).isFalse();
        verifyNoInteractions(studentRepo, brevo);
    }
}
