package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.Invoice;
import ca.vicilearning.dashboard.domain.Membership;
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

        @Test
        void parsesCategoryAndLocation_whenPresent() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","name":"1hr Session","duration":"60","is_visible":"1",
                          "category":"One-on-One","service_category":"At Home"}}
                    """);

            List<Service> services = adapter.toServices(json);

            assertThat(services.get(0).getCategory()).isEqualTo("One-on-One");
            assertThat(services.get(0).getLocation()).isEqualTo("At Home");
        }

        @Test
        void categoryAndLocationAreNull_whenAbsent() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","name":"Session","duration":"60","is_visible":"1"}}
                    """);

            List<Service> services = adapter.toServices(json);

            assertThat(services.get(0).getCategory()).isNull();
            assertThat(services.get(0).getLocation()).isNull();
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

        // Captured verbatim from the live admin getBookings response (2026-07-20): the full
        // datetime lives in "start_date"/"end_date" (no separate start_time), the confirm flag is
        // "is_confirm" (not "is_confirmed"), the provider is "unit_id", the service is "event_id".
        // Guards the two bugs real data exposed: null start times and always-"confirmed" status.
        @Test
        void realAdminApiShape_datetimeInStartDate_andIsConfirm() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","client_id":"1","unit_id":"2","event_id":"3",
                          "start_date":"2026-07-20 11:00:00","end_date":"2026-07-20 12:00:00",
                          "is_confirm":"1"}}
                    """);

            List<Booking> bookings = adapter.toBookings(json,
                    Map.of(1L, studentWithId(1L)),
                    Map.of(2L, tutorWithId(2L)),
                    Map.of(3L, serviceWithId(3L)));

            assertThat(bookings).hasSize(1);
            Booking b = bookings.get(0);
            assertThat(b.getStartTime()).isEqualTo(LocalDateTime.of(2026, 7, 20, 11, 0, 0)); // not null
            assertThat(b.getEndTime()).isEqualTo(LocalDateTime.of(2026, 7, 20, 12, 0, 0));
            assertThat(b.getStatus()).isEqualTo("confirmed"); // via is_confirm, not defaulted
            assertThat(b.getTutor()).isNotNull();   // unit_id resolved
            assertThat(b.getService()).isNotNull(); // event_id resolved
        }

        @Test
        void readsEventCategoryAndLocationOffTheBooking() throws Exception {
            // Real getBookings carries the session category + delivery location per booking
            // (confirmed 2026-07-23): "event_category" and "location".
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","client_id":"1","event_id":"3","is_confirm":"1",
                          "start_date":"2026-07-20 11:00:00",
                          "event_category":"Private 1:1","location":"Virtual Tutoring"}}
                    """);

            List<Booking> bookings = adapter.toBookings(json,
                    Map.of(1L, studentWithId(1L)), Map.of(), Map.of(3L, serviceWithId(3L)));

            assertThat(bookings.get(0).getCategory()).isEqualTo("Private 1:1");
            assertThat(bookings.get(0).getLocation()).isEqualTo("Virtual Tutoring");
        }

        @Test
        void isConfirmZero_mapsToPending() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","client_id":"1","event_id":"3",
                          "start_date":"2026-07-20 11:00:00","is_confirm":"0"}}
                    """);

            List<Booking> bookings = adapter.toBookings(json,
                    Map.of(1L, studentWithId(1L)), Map.of(), Map.of(3L, serviceWithId(3L)));

            assertThat(bookings.get(0).getStatus()).isEqualTo("pending");
        }

        @Test
        void cancelDate_marksBookingCancelled() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"1":{"id":"1","client_id":"1","event_id":"3","is_confirm":"1",
                          "start_date":"2026-07-20 11:00:00","cancel_date":"2026-07-19 09:00:00"}}
                    """);

            List<Booking> bookings = adapter.toBookings(json,
                    Map.of(1L, studentWithId(1L)), Map.of(), Map.of(3L, serviceWithId(3L)));

            assertThat(bookings.get(0).getStatus()).isEqualTo("cancelled");
            assertThat(bookings.get(0).getCancelledAt()).isEqualTo(LocalDateTime.of(2026, 7, 19, 9, 0, 0));
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

    // ── InvoiceAdapter ────────────────────────────────────────────────────────

    @Nested
    class InvoiceAdapterTests {

        private final InvoiceAdapter adapter = new InvoiceAdapter();

        @Test
        void parsesAndLinksToStudentByClientId() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"data":[
                      {"id":"501","client_id":"1","number":"2026-A","status":"Pending",
                       "amount":"150.00","currency":"CAD","datetime":"2026-06-01 09:30:00"}
                    ]}
                    """);
            Student alice = studentWithId(1L);

            List<Invoice> invoices = adapter.toInvoices(json, Map.of(1L, alice));

            assertThat(invoices).hasSize(1);
            Invoice inv = invoices.get(0);
            assertThat(inv.getId()).isEqualTo(501L);
            assertThat(inv.getStudent()).isSameAs(alice);
            assertThat(inv.getNumber()).isEqualTo("2026-A");
            assertThat(inv.getStatus()).isEqualTo("pending");          // lower-cased
            assertThat(inv.isPaid()).isFalse();
            assertThat(inv.getAmount()).isEqualByComparingTo("150.00");
            assertThat(inv.getIssuedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 30, 0));
        }

        @Test
        void keepsInvoiceWithNullStudent_whenClientNotTracked() throws Exception {
            JsonNode json = mapper.readTree("""
                    [{"id":"502","client_id":"999","total":"80","status":"paid"}]
                    """);

            List<Invoice> invoices = adapter.toInvoices(json, Map.of());  // 999 not present

            assertThat(invoices).hasSize(1);
            assertThat(invoices.get(0).getStudent()).isNull();
            assertThat(invoices.get(0).isPaid()).isTrue();
            assertThat(invoices.get(0).getAmount()).isEqualByComparingTo("80");   // "total" fallback
        }

        @Test
        void nestedClientIdAndMissingAmountTolerated() throws Exception {
            JsonNode json = mapper.readTree("""
                    [{"id":"503","client":{"id":"1"},"status":"cancelled"}]
                    """);

            List<Invoice> invoices = adapter.toInvoices(json, Map.of(1L, studentWithId(1L)));

            assertThat(invoices.get(0).getStudent()).isNotNull();   // resolved via nested client.id
            assertThat(invoices.get(0).getAmount()).isNull();       // no amount field → null, not 0
        }

        @Test
        void skipsEntryWithZeroId() throws Exception {
            JsonNode json = mapper.readTree("""
                    [{"id":"0","status":"paid"},{"id":"504","status":"paid"}]
                    """);

            List<Invoice> invoices = adapter.toInvoices(json, Map.of());

            assertThat(invoices).hasSize(1);
            assertThat(invoices.get(0).getId()).isEqualTo(504L);
        }
    }

    // ── MembershipAdapter ─────────────────────────────────────────────────────

    @Nested
    class MembershipAdapterTests {

        private final MembershipAdapter adapter = new MembershipAdapter();

        @Test
        void parsesRemainingBalanceAndActiveFlag() throws Exception {
            JsonNode json = mapper.readTree("""
                    {"data":[
                      {"id":"9","client_id":"1","name":"10-Pack","is_active":"1","remaining":"3",
                       "start_date":"2026-05-01","end_date":"2026-08-01"}
                    ]}
                    """);
            Student alice = studentWithId(1L);

            List<Membership> memberships = adapter.toMemberships(json, Map.of(1L, alice));

            assertThat(memberships).hasSize(1);
            Membership m = memberships.get(0);
            assertThat(m.getStudent()).isSameAs(alice);
            assertThat(m.getName()).isEqualTo("10-Pack");
            assertThat(m.isActive()).isTrue();
            assertThat(m.getRemainingCount()).isEqualTo(3);
            assertThat(m.getStartDate()).isEqualTo(LocalDateTime.of(2026, 5, 1, 0, 0, 0));
        }

        @Test
        void zeroRemainingIsKept_notTreatedAsMissing() throws Exception {
            // "rest" is the remaining balance (real REST v2 field); "count" is the package total and
            // must NOT be read as the balance.
            JsonNode json = mapper.readTree("""
                    [{"id":"9","client_id":"1","name":"Pack","active":true,"rest":"0","count":"10"}]
                    """);

            List<Membership> memberships = adapter.toMemberships(json, Map.of(1L, studentWithId(1L)));

            // 0 is a real balance ("can't book at 0"), must not be dropped to null — and must be the
            // "rest" value, not the total "count" of 10.
            assertThat(memberships.get(0).getRemainingCount()).isEqualTo(0);
        }

        @Test
        void parsesRealRestV2Shape_nestedNameRestPeriodDatesAndCanBeUsed() throws Exception {
            // Captured verbatim from the live REST v2 /admin/clients/memberships (2026-07-23):
            // remaining is "rest", "count" is the package total, name is nested under "membership",
            // dates are period_start/period_end, and there is no is_active — use can_be_used.
            JsonNode json = mapper.readTree("""
                    {"data":[
                      {"id":"77","client_id":"1","rest":"4","count":"8","can_be_used":true,
                       "is_expired":false,"status":"Active",
                       "period_start":"2026-01-15 00:00:00","period_end":"2027-01-15 00:00:00",
                       "membership":{"id":"3","name":"One-on-One Virtual (8 x 1 hr)"}}
                    ]}
                    """);

            List<Membership> memberships = adapter.toMemberships(json, Map.of(1L, studentWithId(1L)));

            Membership m = memberships.get(0);
            assertThat(m.getName()).isEqualTo("One-on-One Virtual (8 x 1 hr)"); // nested membership.name
            assertThat(m.getRemainingCount()).isEqualTo(4);                     // "rest", not "count"=8
            assertThat(m.isActive()).isTrue();                                  // via can_be_used
            assertThat(m.getStartDate()).isEqualTo(LocalDateTime.of(2026, 1, 15, 0, 0, 0));
            assertThat(m.getEndDate()).isEqualTo(LocalDateTime.of(2027, 1, 15, 0, 0, 0));
        }

        @Test
        void expiredMembership_isInactive() throws Exception {
            JsonNode json = mapper.readTree("""
                    [{"id":"78","client_id":"1","rest":"0","is_expired":true,
                      "membership":{"name":"Old Pack"}}]
                    """);

            List<Membership> memberships = adapter.toMemberships(json, Map.of(1L, studentWithId(1L)));

            assertThat(memberships.get(0).isActive()).isFalse();   // is_expired → inactive
        }

        @Test
        void defaultsActiveTrue_whenFlagAbsent() throws Exception {
            JsonNode json = mapper.readTree("""
                    [{"id":"9","client_id":"1","name":"Pack"}]
                    """);

            List<Membership> memberships = adapter.toMemberships(json, Map.of(1L, studentWithId(1L)));

            assertThat(memberships.get(0).isActive()).isTrue();
            assertThat(memberships.get(0).getRemainingCount()).isNull();   // no balance field
        }
    }

    private static Student studentWithId(long id) {
        Student s = new Student();
        s.setId(id);
        s.setName("Test Student");
        return s;
    }
}
