package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.Service;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.Tutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ── ClientAdapter ─────────────────────────────────────────────────────────

    @Nested
    class ClientAdapterTests {

        private final ClientAdapter adapter = new ClientAdapter();

        @Test
        void objectFormat_parsesAllClients() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","name":"Alice Smith","email":"alice@test.com",
                          "phone":"604-555-0101","created_date":"2026-01-15 10:00:00"},
                     "2":{"id":"2","name":"Bob Jones","email":"bob@test.com",
                          "created_date":"2026-02-20"}}
                    """);

            List<Student> students = adapter.toStudents(json);

            assertThat(students).hasSize(2);
            assertThat(students.get(0).getName()).isEqualTo("Alice Smith");
            assertThat(students.get(0).getEmail()).isEqualTo("alice@test.com");
            assertThat(students.get(0).getCreatedAt())
                    .isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 0, 0));
            assertThat(students.get(1).getCreatedAt())
                    .isEqualTo(LocalDateTime.of(2026, 2, 20, 0, 0, 0));
        }

        @Test
        void arrayFormat_parsesAllClients() throws Exception {
            JsonNode json = mapper.readTree("""
                    [{"id":"1","name":"Alice"},{"id":"2","name":"Bob"}]
                    """);

            List<Student> students = adapter.toStudents(json);

            assertThat(students).hasSize(2);
        }

        @Test
        void paginatedFormat_parsesDataField() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"data":[{"id":"1","name":"Alice"},{"id":"2","name":"Bob"}],"total":2}
                    """);

            List<Student> students = adapter.toStudents(json);

            assertThat(students).hasSize(2);
        }

        @Test
        void skipsEntryWithZeroId() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"0":{"id":"0","name":"Invalid"},"1":{"id":"1","name":"Valid"}}
                    """);

            List<Student> students = adapter.toStudents(json);

            assertThat(students).hasSize(1);
            assertThat(students.get(0).getName()).isEqualTo("Valid");
        }

        @Test
        void blankEmailBecomesNull() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","name":"Alice","email":"  "}}
                    """);

            List<Student> students = adapter.toStudents(json);

            assertThat(students.get(0).getEmail()).isNull();
        }

        // ── extractAccountId (REST v2 Client_DetailsEntity) ──

        @Test
        void extractAccountId_matchesByFieldTitle() throws Exception {
            JsonNode details = mapper.readTree("""
                    {"id":2,"fields":[
                      {"id":"name","field":{"id":"name","title":"Name","type":"text"},"value":"Alice"},
                      {"id":"f_acc","field":{"id":"f_acc","title":"Account_ID","type":"text"},"value":"VICI-001"}
                    ]}
                    """);

            assertThat(adapter.extractAccountId(details, "Account_ID")).isEqualTo("VICI-001");
        }

        @Test
        void extractAccountId_isCaseInsensitiveAndTrimsBlankToNull() throws Exception {
            JsonNode details = mapper.readTree("""
                    {"id":2,"fields":[
                      {"id":"f_acc","field":{"id":"f_acc","title":"Account_ID","type":"text"},"value":"  "}
                    ]}
                    """);

            // Blank value → null even though the field exists.
            assertThat(adapter.extractAccountId(details, "account_id")).isNull();
        }

        @Test
        void extractAccountId_returnsNullWhenFieldAbsent() throws Exception {
            JsonNode details = mapper.readTree("""
                    {"id":2,"fields":[
                      {"id":"name","field":{"id":"name","title":"Name","type":"text"},"value":"Alice"}
                    ]}
                    """);

            assertThat(adapter.extractAccountId(details, "Account_ID")).isNull();
        }
    }

    // ── PerformerAdapter ──────────────────────────────────────────────────────

    @Nested
    class PerformerAdapterTests {

        private final PerformerAdapter adapter = new PerformerAdapter();

        @Test
        void parsesIsVisibleString() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","name":"Tutor A","is_visible":"1"},
                     "2":{"id":"2","name":"Tutor B","is_visible":"0"}}
                    """);

            List<Tutor> tutors = adapter.toTutors(json);

            assertThat(tutors.get(0).isActive()).isTrue();
            assertThat(tutors.get(1).isActive()).isFalse();
        }

        @Test
        void parsesIsVisibleBoolean() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","name":"Tutor A","is_visible":true}}
                    """);

            List<Tutor> tutors = adapter.toTutors(json);

            assertThat(tutors.get(0).isActive()).isTrue();
        }
    }

    // ── ServiceAdapter ────────────────────────────────────────────────────────

    @Nested
    class ServiceAdapterTests {

        private final ServiceAdapter adapter = new ServiceAdapter();

        @Test
        void parsesDurationAndActive() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","name":"Math Tutoring","duration":"60","is_visible":"1"},
                     "2":{"id":"2","name":"Science","duration":"90","is_visible":"0"}}
                    """);

            List<Service> services = adapter.toServices(json);

            assertThat(services.get(0).getDurationMinutes()).isEqualTo(60);
            assertThat(services.get(0).isActive()).isTrue();
            assertThat(services.get(1).getDurationMinutes()).isEqualTo(90);
            assertThat(services.get(1).isActive()).isFalse();
        }

        @Test
        void zeroDurationBecomesNull() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","name":"Session","duration":"0","is_visible":"1"}}
                    """);

            List<Service> services = adapter.toServices(json);

            assertThat(services.get(0).getDurationMinutes()).isNull();
        }
    }

    // ── BookingAdapter ────────────────────────────────────────────────────────

    @Nested
    class BookingAdapterTests {

        private final BookingAdapter adapter = new BookingAdapter();

        @Test
        void happyPath_splitDateAndTime() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"101":{"id":"101","client_id":"1","provider_id":"1","service_id":"1",
                            "start_date":"2026-06-15","start_time":"10:00:00",
                            "end_date":"2026-06-15","end_time":"11:00:00",
                            "status":"confirmed","cancel_date":null}}
                    """);

            Student student = studentWithId(1L);
            Tutor tutor = tutorWithId(1L);
            Service service = serviceWithId(1L);

            List<Booking> bookings = adapter.toBookings(
                    json, Map.of(1L, student), Map.of(1L, tutor), Map.of(1L, service));

            assertThat(bookings).hasSize(1);
            assertThat(bookings.get(0).getId()).isEqualTo(101L);
            assertThat(bookings.get(0).getStatus()).isEqualTo("confirmed");
            assertThat(bookings.get(0).getStartTime())
                    .isEqualTo(LocalDateTime.of(2026, 6, 15, 10, 0, 0));
            assertThat(bookings.get(0).getCancelledAt()).isNull();
        }

        @Test
        void happyPath_combinedDateTime() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"101":{"id":"101","client_id":"1","provider_id":"1","service_id":"1",
                            "start_date_time":"2026-06-15 10:00:00",
                            "end_date_time":"2026-06-15 11:00:00",
                            "status":"confirmed"}}
                    """);

            List<Booking> bookings = adapter.toBookings(json,
                    Map.of(1L, studentWithId(1L)),
                    Map.of(1L, tutorWithId(1L)),
                    Map.of(1L, serviceWithId(1L)));

            assertThat(bookings.get(0).getStartTime())
                    .isEqualTo(LocalDateTime.of(2026, 6, 15, 10, 0, 0));
        }

        @Test
        void skipsMissingStudent() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"101":{"id":"101","client_id":"999","service_id":"1",
                            "start_date":"2026-06-15","start_time":"10:00:00","status":"confirmed"}}
                    """);

            List<Booking> bookings = adapter.toBookings(json,
                    Map.of(),                       // student 999 not present
                    Map.of(),
                    Map.of(1L, serviceWithId(1L)));

            assertThat(bookings).isEmpty();
        }

        @Test
        void nullTutorAllowed() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"101":{"id":"101","client_id":"1","provider_id":"0","service_id":"1",
                            "start_date":"2026-06-15","start_time":"10:00:00","status":"confirmed"}}
                    """);

            List<Booking> bookings = adapter.toBookings(json,
                    Map.of(1L, studentWithId(1L)),
                    Map.of(),
                    Map.of(1L, serviceWithId(1L)));

            assertThat(bookings).hasSize(1);
            assertThat(bookings.get(0).getTutor()).isNull();
        }

        // ── helpers ──

        private Student studentWithId(long id) {
            Student s = new Student();
            s.setId(id);
            s.setName("Test");
            return s;
        }

        private Tutor tutorWithId(long id) {
            Tutor t = new Tutor();
            t.setId(id);
            t.setName("Tutor");
            return t;
        }

        private Service serviceWithId(long id) {
            Service s = new Service();
            s.setId(id);
            s.setName("Service");
            return s;
        }
    }
}
