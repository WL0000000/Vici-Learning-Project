package ca.vicilearning.dashboard.student;

import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentStatusServiceTest {

    @Mock
    private StudentRepository studentRepo;

    @InjectMocks
    private StudentStatusService service;

    @Test
    void setsStatusOnKnownStudent() {
        Student s = new Student();
        s.setStatus(StudentStatus.ACTIVE);
        when(studentRepo.findById(5L)).thenReturn(Optional.of(s));

        boolean updated = service.setStatus(5L, "PAUSED");

        assertThat(updated).isTrue();
        assertThat(s.getStatus()).isEqualTo(StudentStatus.PAUSED);
        verify(studentRepo).save(s);
    }

    @Test
    void acceptsLowercaseAndTrimsWhitespace() {
        Student s = new Student();
        when(studentRepo.findById(1L)).thenReturn(Optional.of(s));

        assertThat(service.setStatus(1L, "  paused ")).isTrue();
        assertThat(s.getStatus()).isEqualTo(StudentStatus.PAUSED);
    }

    @Test
    void noOpWhenStudentUnknown() {
        when(studentRepo.findById(9L)).thenReturn(Optional.empty());

        assertThat(service.setStatus(9L, "PAUSED")).isFalse();
        verify(studentRepo, never()).save(any());
    }

    @Test
    void noOpForUnrecognizedStatus() {
        // Guards against a malformed request 500ing or blanking the column.
        assertThat(service.setStatus(5L, "BOGUS")).isFalse();
        verifyNoInteractions(studentRepo);
    }

    @Test
    void noOpForNullStatus() {
        assertThat(service.setStatus(5L, null)).isFalse();
        verifyNoInteractions(studentRepo);
    }
}
