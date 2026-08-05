package ca.vicilearning.dashboard.metrics;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.BookingRepository;
import ca.vicilearning.dashboard.domain.Invoice;
import ca.vicilearning.dashboard.domain.InvoiceRepository;
import ca.vicilearning.dashboard.domain.Membership;
import ca.vicilearning.dashboard.domain.MembershipRepository;
import ca.vicilearning.dashboard.domain.RosterStudent;
import ca.vicilearning.dashboard.domain.RosterStudentRepository;
import ca.vicilearning.dashboard.domain.Service;
import ca.vicilearning.dashboard.domain.ServiceRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import ca.vicilearning.dashboard.domain.Tutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardMetricsServiceTest {

    @Mock BookingRepository bookingRepo;
    @Mock StudentRepository studentRepo;
    @Mock RosterStudentRepository rosterStudentRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock ServiceRepository serviceRepo;
    @Mock MembershipRepository membershipRepo;
    DashboardMetricsService service;

    // Membership low-balance threshold of 2 for tests (a family with ≤2 sessions left is "low").
    private static final int LOW_THRESHOLD = 2;
    // Lapse threshold of 21 days for tests (matches the production default).
    private static final int LAPSE_THRESHOLD = 21;

    private final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new DashboardMetricsService(
                bookingRepo, studentRepo, rosterStudentRepo, invoiceRepo, serviceRepo, membershipRepo,
                LOW_THRESHOLD, LAPSE_THRESHOLD);
    }

    /** A roster student (the Brevo-sourced roster), keyed by EXT_ID, ACTIVE by default. */
    private RosterStudent rosterStudent(String extId, String name, String accountId) {
        RosterStudent r = new RosterStudent();
        r.setExtId(extId);
        r.setName(name);
        r.setAccountId(accountId);
        return r;
    }

    @Test
    void overview_countsSessionsAndHours_excludingCancelled() {
        // 1h + 2h confirmed = 3h over 2 sessions; the cancelled one is excluded from both.
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, null, "confirmed", now, 60),
                booking(2, null, "confirmed", now, 120),
                booking(3, null, "cancelled", now, 60)));
        // "Active students" is the CURRENT (ACTIVE+PAUSED) count on the Brevo roster; DROPPED excluded.
        RosterStudent active = rosterStudent("E1", "A", "f");
        RosterStudent paused = rosterStudent("E2", "B", "f");
        paused.setStatus(StudentStatus.PAUSED);
        RosterStudent dropped = rosterStudent("E3", "C", "f");
        dropped.setStatus(StudentStatus.DROPPED);
        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(active, paused, dropped));

        DashboardMetricsService.Overview o = service.overview();

        assertThat(o.activeStudents()).isEqualTo(2L);   // ACTIVE + PAUSED current; DROPPED not counted
        assertThat(o.sessionsThisWeek()).isEqualTo(2);
        assertThat(o.hoursThisWeek()).isEqualTo(3.0);
        // Same stubbed list is used for the month query → one cancelled booking.
        assertThat(o.cancellationsThisMonth()).isEqualTo(1);
    }

    @Test
    void weeklyHours_bucketsCurrentWeek_andExcludesCancelled() {
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, null, "confirmed", now, 60),
                booking(2, null, "confirmed", now, 120),
                booking(3, null, "cancelled", now, 60)));

        // Just the current week.
        List<DashboardMetricsService.PeriodHours> weeks = service.hoursByPeriod(DashboardMetricsService.PeriodUnit.WEEK, 0, 0); //weeklyHours(0, 0) and DashboardMetricsService.WeeklyHours were replaced

        assertThat(weeks).hasSize(1);
        assertThat(weeks.get(0).hours()).isEqualTo(3.0);
        assertThat(weeks.get(0).sessions()).isEqualTo(2);
    }

    @Test
    void hoursByPeriod_month_bucketsCurrentMonth_andExcludesCancelled() {
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, null, "confirmed", now, 60),
                booking(2, null, "confirmed", now, 120),
                booking(3, null, "cancelled", now, 60)));

        List<DashboardMetricsService.PeriodHours> months =
                service.hoursByPeriod(DashboardMetricsService.PeriodUnit.MONTH, 0, 0);

        assertThat(months).hasSize(1);
        assertThat(months.get(0).hours()).isEqualTo(3.0);
        assertThat(months.get(0).sessions()).isEqualTo(2);
    }

    @Test
    void hoursByPeriod_year_bucketsCurrentYear() {
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, null, "confirmed", now, 60)));

        List<DashboardMetricsService.PeriodHours> years =
                service.hoursByPeriod(DashboardMetricsService.PeriodUnit.YEAR, 1, 0);

        // 1 year back + current year = 2 buckets.
        assertThat(years).hasSize(2);
        assertThat(years.get(1).hours()).isEqualTo(1.0);
        assertThat(years.get(0).hours()).isEqualTo(0.0);
    }

    @Test
    void hoursInRange_sumsHoursAndSessions_excludingCancelled() {
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, null, "confirmed", now, 60),
                booking(2, null, "confirmed", now, 120),
                booking(3, null, "cancelled", now, 90)));

        DashboardMetricsService.RangeHours range = service.hoursInRange(now.toLocalDate(), now.toLocalDate().plusDays(1));

        assertThat(range.hours()).isEqualTo(3.0);
        assertThat(range.sessions()).isEqualTo(2);
    }

    @Test
    void tutorHoursForRange_sortsByHoursDescByDefault_orByNameWhenRequested() {
        Tutor amy = tutor("Amy");
        Tutor zack = tutor("Zack");
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                bookingWithTutor(1, amy, "confirmed", now, 60),
                bookingWithTutor(2, zack, "confirmed", now, 180)));

        List<DashboardMetricsService.TutorHours> byHours =
                service.tutorHoursForRange(now.toLocalDate(), now.toLocalDate().plusDays(1), false);
        assertThat(byHours).extracting(DashboardMetricsService.TutorHours::tutorName)
                .containsExactly("Zack", "Amy");

        List<DashboardMetricsService.TutorHours> byName =
                service.tutorHoursForRange(now.toLocalDate(), now.toLocalDate().plusDays(1), true);
        assertThat(byName).extracting(DashboardMetricsService.TutorHours::tutorName)
                .containsExactly("Amy", "Zack");
    }

    private Tutor tutor(String name) {
        Tutor t = new Tutor();
        t.setName(name);
        return t;
    }

    private Booking bookingWithTutor(long id, Tutor tutor, String status, LocalDateTime start, int minutes) {
        Booking b = booking(id, null, status, start, minutes);
        b.setTutor(tutor);
        return b;
    }

    @Test
    void studentRows_listsRosterStudentsSortedByName() {
        // The roster is the Brevo-sourced students, keyed by EXT_ID — identity/family/status, no hours.
        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                rosterStudent("E2", "Bob", "VICI-0002"),
                rosterStudent("E1", "Alice", "VICI-0001")));

        List<DashboardMetricsService.StudentRow> rows = service.studentRows();

        assertThat(rows).extracting(DashboardMetricsService.StudentRow::name)
                .containsExactly("Alice", "Bob");
        assertThat(rows.get(0).id()).isEqualTo("E1");         // id is the EXT_ID (the toggle key)
        assertThat(rows.get(0).extId()).isEqualTo("E1");
        assertThat(rows.get(0).accountId()).isEqualTo("VICI-0001");
        assertThat(rows.get(0).status()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    void studentRows_derivesPerStudentHoursForSoloFamily_familyTotalForSiblings() {
        // Solo family VICI-1 (one roster student) vs sibling family VICI-2 (two roster students).
        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                rosterStudent("E1", "Solo Kid", "VICI-1"),
                rosterStudent("E2", "Sib One", "VICI-2"),
                rosterStudent("E3", "Sib Two", "VICI-2")));
        // This week: 2h on the solo family's account, 3h on the sibling family's account.
        Student acct1 = studentWithAccount(1L, "Acct1", "VICI-1");
        Student acct2 = studentWithAccount(2L, "Acct2", "VICI-2");
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, acct1, "confirmed", now, 120),
                booking(2, acct2, "confirmed", now, 180)));

        List<DashboardMetricsService.StudentRow> rows = service.studentRows();

        DashboardMetricsService.StudentRow solo = row(rows, "Solo Kid");
        assertThat(solo.hoursPerStudent()).isTrue();          // unambiguous — one student in the family
        assertThat(solo.weeklyHours()).isEqualTo(2.0);
        assertThat(solo.weeklySessions()).isEqualTo(1);

        DashboardMetricsService.StudentRow sibling = row(rows, "Sib One");
        assertThat(sibling.hoursPerStudent()).isFalse();      // shared — can't split between siblings
        assertThat(sibling.weeklyHours()).isEqualTo(3.0);     // the family total
    }

    @Test
    void studentRows_unassignedStudentHasNoDerivableHours() {
        when(rosterStudentRepo.findByDeletedAtIsNull())
                .thenReturn(List.of(rosterStudent("E1", "No Family", null)));
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of());

        DashboardMetricsService.StudentRow r = service.studentRows().get(0);

        assertThat(r.weeklyHours()).isNull();
        assertThat(r.weeklySessions()).isNull();
        assertThat(r.hoursPerStudent()).isFalse();
    }

    private static DashboardMetricsService.StudentRow row(
            List<DashboardMetricsService.StudentRow> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.name())).findFirst().orElseThrow();
    }

    @Test
    void studentRows_statusFilter_returnsOnlyMatchingStatus() {
        RosterStudent alice = rosterStudent("E1", "Alice", "f");   // ACTIVE by default
        RosterStudent bob = rosterStudent("E2", "Bob", "f");
        bob.setStatus(StudentStatus.PAUSED);
        when(rosterStudentRepo.findByDeletedAtIsNull()).thenReturn(List.of(alice, bob));

        // Filtering to PAUSED drops Alice; null would return both.
        List<DashboardMetricsService.StudentRow> paused = service.studentRows(null, StudentStatus.PAUSED);

        assertThat(paused).extracting(DashboardMetricsService.StudentRow::name).containsExactly("Bob");
        assertThat(paused.get(0).status()).isEqualTo(StudentStatus.PAUSED);
    }

    @Test
    void familyGroups_groupsSiblingsFromRoster_withFamilyHoursFromSimplyBook() {
        // Roster members: two siblings share VICI-0001 (a family); a lone roster student stands apart.
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull()).thenReturn(List.of(
                rosterStudent("E1", "Sam Tran", "VICI-0001"),
                rosterStudent("E2", "Sara Tran", "VICI-0001"),
                rosterStudent("E3", "Ivy Kim", "VICI-0002")));
        // SimplyBook accounts under VICI-0001 supply the family's hours (1h + 2h = 3h over 2 sessions).
        Student acct1 = studentWithAccount(1L, "Tran Acct A", "VICI-0001");
        Student acct2 = studentWithAccount(2L, "Tran Acct B", "VICI-0001");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(acct1, acct2));
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, acct1, "confirmed", now, 60),
                booking(2, acct2, "confirmed", now, 120)));

        List<DashboardMetricsService.FamilyGroup> families = service.familyGroups();

        // Only VICI-0001 has 2+ roster members; the lone Ivy is excluded.
        assertThat(families).hasSize(1);
        DashboardMetricsService.FamilyGroup tran = families.get(0);
        assertThat(tran.accountId()).isEqualTo("VICI-0001");
        assertThat(tran.size()).isEqualTo(2);
        assertThat(tran.members()).extracting(DashboardMetricsService.FamilyMember::name)
                .containsExactly("Sam Tran", "Sara Tran");   // roster members, sorted by name
        assertThat(tran.sessionsThisWeek()).isEqualTo(2);
        assertThat(tran.hoursThisWeek()).isEqualTo(3.0);     // family total, from the SimplyBook side
    }

    @Test
    void familyActivityByAccount_includesSingleStudentFamilies_unlikeFamilyGroups() {
        // A lone family (one SimplyBook account) with a 2h booking. familyGroups drops it (needs 2+
        // roster members), but the Association drill-down needs its rollup.
        Student acct = studentWithAccount(1L, "Solo Acct", "Solo_Account");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(acct));
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, acct, "confirmed", now, 120)));
        when(bookingRepo.findActiveWithStudentAndService()).thenReturn(List.of());
        when(membershipRepo.findByDeletedAtIsNull()).thenReturn(List.of());

        var activity = service.familyActivityByAccount(null);

        assertThat(activity).containsKey("Solo_Account");
        assertThat(activity.get("Solo_Account").hoursThisWeek()).isEqualTo(2.0);
        assertThat(activity.get("Solo_Account").sessionsThisWeek()).isEqualTo(1);
    }

    @Test
    void familyGroups_aggregatesCategoriesLocationsAndMembershipsFromSimplyBook() {
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull()).thenReturn(List.of(
                rosterStudent("E1", "Sam Tran", "VICI-0001"),
                rosterStudent("E2", "Sara Tran", "VICI-0001")));
        Student acct1 = studentWithAccount(1L, "Tran Acct A", "VICI-0001");
        Student acct2 = studentWithAccount(2L, "Tran Acct B", "VICI-0001");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(acct1, acct2));
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of());
        // Category/location come from the family's SimplyBook bookings; the duplicate collapses.
        when(bookingRepo.findActiveWithStudentAndService()).thenReturn(List.of(
                svcBooking(1, acct1, "One-on-One", "Virtual Tutoring"),
                svcBooking(2, acct2, "Study Club", "At Home"),
                svcBooking(3, acct1, "One-on-One", "Virtual Tutoring")));
        when(membershipRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                membership(acct1, 8), membership(acct2, 3)));

        DashboardMetricsService.FamilyGroup fam = service.familyGroups().get(0);

        assertThat(fam.categories()).containsExactly("One-on-One", "Study Club");
        assertThat(fam.locations()).containsExactly("At Home", "Virtual Tutoring");
        assertThat(fam.memberships())
                .extracting(DashboardMetricsService.MembershipSummary::sessionsLeft)
                .containsExactlyInAnyOrder(8, 3);
    }

    @Test
    void familyGroups_summarisesUnlimitedExpiryAndInvoice() {
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull()).thenReturn(List.of(
                rosterStudent("E1", "Sam Tran", "VICI-0001"),
                rosterStudent("E2", "Sara Tran", "VICI-0001")));
        Student acct1 = studentWithAccount(1L, "Tran Acct A", "VICI-0001");
        Student acct2 = studentWithAccount(2L, "Tran Acct B", "VICI-0001");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(acct1, acct2));
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of());

        Membership counted = membership(acct1, 5);
        counted.setEndDate(LocalDateTime.of(2026, 8, 1, 0, 0));
        counted.setInvoiceNumber("SI-2026000096");
        Membership unlimited = membership(acct2, 0);   // remaining ignored once unlimited
        unlimited.setUnlimited(true);
        when(membershipRepo.findByDeletedAtIsNull()).thenReturn(List.of(counted, unlimited));

        DashboardMetricsService.FamilyGroup fam = service.familyGroups().get(0);

        DashboardMetricsService.MembershipSummary samSummary = fam.memberships().stream()
                .filter(m -> !m.unlimited()).findFirst().orElseThrow();
        assertThat(samSummary.sessionsLeft()).isEqualTo(5);
        assertThat(samSummary.expires()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(samSummary.invoiceNumber()).isEqualTo("SI-2026000096");

        DashboardMetricsService.MembershipSummary saraSummary = fam.memberships().stream()
                .filter(DashboardMetricsService.MembershipSummary::unlimited).findFirst().orElseThrow();
        assertThat(saraSummary.sessionsLeft()).isNull();   // unlimited → no countdown shown
    }

    @Test
    void actionRequired_doesNotFlagUnlimitedMembershipAsEmpty() {
        // Latest membership is unlimited (remaining 0 is irrelevant) → no MEMBERSHIP_EMPTY/LOW item.
        Student u = student(1L, "Unlimited Uma");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(u));
        when(bookingRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                booking(1, u, "confirmed", now.minusDays(1), 60)));   // recent → no NO_BOOKING item
        Membership unlimited = membership(u, 0);
        unlimited.setUnlimited(true);
        when(membershipRepo.findByDeletedAtIsNull()).thenReturn(List.of(unlimited));

        assertThat(service.actionRequired()).extracting(DashboardMetricsService.ActionItem::type)
                .doesNotContain("MEMBERSHIP_EMPTY", "MEMBERSHIP_LOW");
    }

    @Test
    void serviceLocationsAndCategories_deriveFromServiceNameWhenStructuredFieldsBlank() {
        // A service with NO structured category/location, but a name that encodes both.
        Service svc = new Service();
        svc.setId(1L);
        svc.setName("One-on-One Virtual (8 x 1 hr)");
        Booking b = booking(1, student(1L, "Kid"), "confirmed", now, 60);
        b.setService(svc);
        when(bookingRepo.findActiveWithStudentAndService()).thenReturn(List.of(b));

        assertThat(service.serviceLocations()).containsExactly("Virtual Tutoring");
        assertThat(service.serviceCategories()).containsExactly("Private 1:1");
    }

    private Booking svcBooking(long id, Student student, String category, String location) {
        Booking b = booking(id, student, "confirmed", now, 60);
        Service svc = new Service();
        svc.setId(id);
        svc.setCategory(category);
        svc.setLocation(location);
        b.setService(svc);
        return b;
    }

    private Membership membership(Student student, int remaining) {
        return membership(student, remaining, true);
    }

    private Membership membership(Student student, int remaining, boolean active) {
        Membership m = new Membership();
        m.setStudent(student);
        m.setRemainingCount(remaining);
        m.setActive(active);
        return m;
    }

    private Student studentWithAccount(long id, String name, String accountId) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setAccountId(accountId);
        return s;
    }

    @Test
    void hoursByPeriod_withLocation_countsOnlyThatLocation() {
        // This week: 1h At Home + 2h Virtual. Filtering to "At Home" counts only the 1h one.
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                bookingAt(1, "confirmed", now, 60, "At Home"),
                bookingAt(2, "confirmed", now, 120, "Virtual Tutoring")));

        double allHours = service.hoursByPeriod(DashboardMetricsService.PeriodUnit.WEEK, 0, 0, null)
                .get(0).hours();
        DashboardMetricsService.PeriodHours atHome =
                service.hoursByPeriod(DashboardMetricsService.PeriodUnit.WEEK, 0, 0,
                        new DashboardMetricsService.ServiceScope("At Home", null)).get(0);

        assertThat(allHours).isEqualTo(3.0);
        assertThat(atHome.hours()).isEqualTo(1.0);
        assertThat(atHome.sessions()).isEqualTo(1);
    }

    private Booking booking(long id, Student student, String status, LocalDateTime start, int minutes) {
        Booking b = new Booking();
        b.setId(id);
        b.setStudent(student);
        b.setStatus(status);
        b.setStartTime(start);
        b.setEndTime(start.plusMinutes(minutes));
        return b;
    }

    /** A confirmed booking whose service is in the given location — for the location-filter test. */
    private Booking bookingAt(long id, String status, LocalDateTime start, int minutes, String location) {
        Booking b = booking(id, null, status, start, minutes);
        Service svc = new Service();
        svc.setId(id);
        svc.setLocation(location);
        b.setService(svc);
        return b;
    }

    private Student student(long id, String name) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setAccountId(String.format("VICI-%04d", id));
        return s;
    }

    @Test
    void actionRequired_flagsNoBookingGap_andCancellationPattern() {
        Student staleStudent = student(1L, "Jane Doe");
        Student healthyStudent = student(2L, "Healthy Student");
        Student cancelHeavyStudent = student(3L, "Cancel Heavy");

        when(studentRepo.findByDeletedAtIsNull())
                .thenReturn(List.of(staleStudent, healthyStudent, cancelHeavyStudent));

        when(bookingRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                // last booking was 30 days ago -> should trigger NO_BOOKING
                booking(1, staleStudent, "confirmed", now.minusDays(30), 60),
                // no flag expected here
                booking(2, healthyStudent, "confirmed", now.minusDays(2), 60),
                // should trigger CANCELLATIONS
                booking(3, cancelHeavyStudent, "cancelled", now, 60),
                booking(4, cancelHeavyStudent, "cancelled", now, 60),
                booking(5, cancelHeavyStudent, "cancelled", now, 60),
                // also has a recent confirmed booking so it should NOT get flagged for no-booking
                booking(6, cancelHeavyStudent, "confirmed", now.minusDays(1), 60)));

        List<DashboardMetricsService.ActionItem> items = service.actionRequired();

        assertThat(items).extracting(DashboardMetricsService.ActionItem::studentName)
                .containsExactlyInAnyOrder("Jane Doe", "Cancel Heavy");

        DashboardMetricsService.ActionItem janeItem = items.stream()
                .filter(i -> i.studentName().equals("Jane Doe")).findFirst().orElseThrow();
        assertThat(janeItem.type()).isEqualTo("NO_BOOKING");
        assertThat(janeItem.reason()).contains("30 days");

        DashboardMetricsService.ActionItem cancelItem = items.stream()
                .filter(i -> i.studentName().equals("Cancel Heavy")).findFirst().orElseThrow();
        assertThat(cancelItem.type()).isEqualTo("CANCELLATIONS");
        assertThat(cancelItem.reason()).contains("3 cancellations");
    }

    @Test
    void actionRequired_collapsesDuplicateSimplyBookRecords_usingRosterNames() {
        // Two SimplyBook client records for ONE family (a real-data duplicate + a dead "(OLD)"), lapsed.
        Student dupA = student(1L, "Amanda Dumont");
        Student dupB = student(2L, "Amanda Dumont (OLD)");
        dupA.setAccountId("Dumont_Account");
        dupB.setAccountId("Dumont_Account");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(dupA, dupB));
        when(bookingRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                booking(1, dupA, "confirmed", now.minusDays(40), 60),
                booking(2, dupB, "confirmed", now.minusDays(40), 60)));
        // The roster knows the real family: a single student, "Amanda Dumont".
        when(rosterStudentRepo.findByDeletedAtIsNullAndAccountIdIsNotNull())
                .thenReturn(List.of(rosterStudent("E1", "Amanda Dumont", "Dumont_Account")));

        List<DashboardMetricsService.ActionItem> items = service.actionRequired();

        // The duplicate records collapse to one item, labelled from the roster (no "(OLD)").
        assertThat(items).hasSize(1);
        assertThat(items.get(0).studentName()).isEqualTo("Amanda Dumont");
        assertThat(items.get(0).type()).isEqualTo("NO_BOOKING");
    }

    @Test
    void actionRequired_returnsEmpty_whenNoStudentsNeedAttention() {
        Student healthyStudent = student(1L, "All Good");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(healthyStudent));
        when(bookingRepo.findByDeletedAtIsNull())
                .thenReturn(List.of(booking(1, healthyStudent, "confirmed", now.minusDays(1), 60)));

        List<DashboardMetricsService.ActionItem> items = service.actionRequired();

        assertThat(items).isEmpty();
    }

    @Test
    void actionRequired_flagsEmptyAndLowMemberships_skippingHealthyAndCancelled() {
        Student empty = student(1L, "Empty Fam");
        Student low = student(2L, "Low Fam");
        Student healthy = student(3L, "Healthy Fam");
        Student cancelled = student(4L, "Cancelled Fam");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(empty, low, healthy, cancelled));
        // Recent bookings for everyone so nobody trips the 21-day NO_BOOKING rule.
        when(bookingRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                booking(1, empty, "confirmed", now.minusDays(1), 60),
                booking(2, low, "confirmed", now.minusDays(1), 60),
                booking(3, healthy, "confirmed", now.minusDays(1), 60),
                booking(4, cancelled, "confirmed", now.minusDays(1), 60)));
        when(membershipRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                membership(empty, 0),            // 0 → MEMBERSHIP_EMPTY
                membership(low, 2),              // 2 <= threshold → MEMBERSHIP_LOW
                membership(healthy, 8),          // healthy → no item
                membership(cancelled, 0, false)));  // 0 but cancelled → not flagged

        List<DashboardMetricsService.ActionItem> items = service.actionRequired();

        assertThat(items).extracting(DashboardMetricsService.ActionItem::type)
                .containsExactlyInAnyOrder("MEMBERSHIP_EMPTY", "MEMBERSHIP_LOW");
        // Empty ("can't book") sorts above low.
        assertThat(items.get(0).type()).isEqualTo("MEMBERSHIP_EMPTY");
    }

    @Test
    void actionRequired_flagsUnpaidInvoices_excludingPaid() {
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of());
        when(bookingRepo.findByDeletedAtIsNull()).thenReturn(List.of());
        Student jane = student(1L, "Jane Doe");
        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(
                invoice(1L, jane, "pending", "75.00", now.minusDays(5)),
                invoice(2L, jane, "paid", "50.00", now.minusDays(5))));

        List<DashboardMetricsService.ActionItem> items = service.actionRequired();

        // Only the unpaid invoice becomes an action item.
        assertThat(items).extracting(DashboardMetricsService.ActionItem::type)
                .containsExactly("INVOICE_UNPAID");
        assertThat(items.get(0).reason()).contains("75.00");
    }

    @Test
    void pendingInvoices_excludesPaid_andSortsOldestIssuedFirst() {
        Student jane = student(1L, "Jane Doe");
        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(
                invoice(1L, jane, "paid", "50.00", now.minusDays(10)),
                invoice(2L, jane, "pending", "75.00", now.minusDays(2)),
                invoice(3L, null, "pending", "25.00", now.minusDays(20))));

        List<DashboardMetricsService.PendingInvoice> pending = service.pendingInvoices(10);

        assertThat(pending).extracting(DashboardMetricsService.PendingInvoice::id)
                .containsExactly(3L, 2L);
        assertThat(pending.get(0).studentName()).isEqualTo("Unlinked client");
    }

    @Test
    void pendingInvoicesSummary_sumsUnpaidAmounts_ignoringPaidAndNullAmounts() {
        Student jane = student(1L, "Jane Doe");
        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(
                invoice(1L, jane, "paid", "50.00", now),
                invoice(2L, jane, "pending", "75.00", now),
                invoice(3L, jane, "pending", null, now)));

        DashboardMetricsService.PendingInvoicesSummary summary = service.pendingInvoicesSummary();

        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    private Invoice invoice(long id, Student student, String status, String amount, LocalDateTime issuedAt) {
        Invoice inv = new Invoice();
        inv.setId(id);
        inv.setStudent(student);
        inv.setStatus(status);
        inv.setAmount(amount == null ? null : new BigDecimal(amount));
        inv.setIssuedAt(issuedAt);
        return inv;
    }
}
