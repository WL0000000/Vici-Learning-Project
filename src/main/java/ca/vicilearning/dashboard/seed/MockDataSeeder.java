package ca.vicilearning.dashboard.seed;

import ca.vicilearning.dashboard.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates realistic mock data so the dashboard can be demoed and load-tested without any
 * live SimplyBook.me / Brevo credentials. It writes the exact same local entities a real
 * SimplyBook sync produces — tutors, services, students (with the Brevo-link {@code accountId}),
 * bookings, invoices, and memberships — so every downstream feature (weekly hours, filters,
 * tutor drill-down, the Families rollup, and the overview cash-flow / pending-invoices section)
 * works against it identically to live data.
 *
 * <p><b>Activation:</b> only runs under the {@code seed} Spring profile, so it can never touch a
 * real deployment. Run with {@code SPRING_PROFILES_ACTIVE=seed} (or
 * {@code mvn spring-boot:run -Dspring-boot.run.profiles=seed}). Control volume with
 * {@code seed.student-count} (default 70; set to 300+ for scale checks).
 *
 * <p><b>Important:</b> do not run the live sync against a seeded DB — the sync soft-deletes any
 * local row it doesn't find upstream, which would mark all seeded rows deleted. Seeding is for
 * offline demo/dev only.
 */
@Component
@Profile("seed")
@Order(1)
public class MockDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MockDataSeeder.class);

    // Fixed seed → identical data on every run and for every teammate, so demos are stable.
    private static final long RANDOM_SEED = 20260623L;

    // Mirror the live sync window so seeded bookings line up with what a real sync would hold.
    private static final int LOOKBACK_DAYS  = 90;
    private static final int LOOKAHEAD_DAYS = 30;

    // Manually assigned id ranges (these entities use upstream ids, not @GeneratedValue).
    private static final long STUDENT_ID_BASE    = 1000L;
    private static final long INVOICE_ID_BASE    = 5000L;
    private static final long MEMBERSHIP_ID_BASE = 8000L;

    // Session price used to size invoice amounts. Mock only — real amounts come from SimplyBook.
    private static final BigDecimal SESSION_PRICE = new BigDecimal("55.00");

    private static final String[] FIRST_NAMES = {
            "Olivia", "Liam", "Emma", "Noah", "Ava", "Ethan", "Sophia", "Mason", "Isabella",
            "Lucas", "Mia", "Aiden", "Charlotte", "Jackson", "Amelia", "Caleb", "Harper",
            "Benjamin", "Ella", "Daniel", "Grace", "Henry", "Chloe", "Samuel", "Zoe"
    };
    private static final String[] LAST_NAMES = {
            "Tran", "Nguyen", "Smith", "Patel", "Chen", "Kim", "Singh", "Wong", "Garcia",
            "Lee", "Brown", "Khan", "Martin", "Wang", "Lam", "Roy", "Dixon", "Ahmed",
            "Park", "Reyes", "Cohen", "Diaz", "Ford", "Gill"
    };
    private static final String[] TUTOR_NAMES = {
            "Corina Vega", "Daniel Osei", "Priya Sharma", "Marcus Bell",
            "Hannah Park", "Tariq Aziz", "Elena Petrova", "Sam Whitfield"
    };

    // Service catalogue: {name, durationMinutes, category, location} — modelled on the real
    // export so the category/location filters have realistic data. Category and location are two
    // distinct axes (Meeting #4): three categories (Private 1:1 / Study Club / Assessment) across
    // four locations. The 120-min entry is why hours must be derived from duration, not session
    // count (a client requirement).
    private static final Object[][] SERVICE_DEFS = {
            {"In-Person 1hr Tutoring Session (Single) for Members", 60, "Private 1:1", "VICI Learning Centre"},
            {"Virtual 1hr Tutoring Session (Single) for Members",   60, "Private 1:1", "Virtual Tutoring"},
            {"At-Home 1hr Tutoring Session (Single) for Members",   60, "Private 1:1", "At Home"},
            {"Study Club (1.5hrs)",                                 90, "Study Club",  "VICI Learning Centre (Study Clubs)"},
            {"2hr Intensive Session (Single) for Members",         120, "Private 1:1", "VICI Learning Centre"},
            {"Assessment (1hr)",                                    60, "Assessment",  "VICI Learning Centre"}
    };

    private final TutorRepository      tutorRepo;
    private final ServiceRepository    serviceRepo;
    private final StudentRepository    studentRepo;
    private final BookingRepository    bookingRepo;
    private final InvoiceRepository    invoiceRepo;
    private final MembershipRepository membershipRepo;
    private final int studentCount;

    public MockDataSeeder(TutorRepository tutorRepo,
                          ServiceRepository serviceRepo,
                          StudentRepository studentRepo,
                          BookingRepository bookingRepo,
                          InvoiceRepository invoiceRepo,
                          MembershipRepository membershipRepo,
                          @Value("${seed.student-count:70}") int studentCount) {
        this.tutorRepo      = tutorRepo;
        this.serviceRepo    = serviceRepo;
        this.studentRepo    = studentRepo;
        this.bookingRepo    = bookingRepo;
        this.invoiceRepo    = invoiceRepo;
        this.membershipRepo = membershipRepo;
        this.studentCount   = studentCount;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            long existing = studentRepo.count();
            if (existing > 0) {
                // Idempotent: never double-seed an already-populated DB.
                log.info("Seed profile active but {} students already exist; skipping seed", existing);
                return;
            }
            seed();
        } catch (Exception e) {
            // Seeding must never crash app startup — log and carry on.
            log.error("Mock data seeding failed: {}", e.getMessage(), e);
        }
    }

    private void seed() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Random rng = new Random(RANDOM_SEED);

        List<Tutor> tutors = buildTutors(now);
        tutorRepo.saveAll(tutors);

        List<Service> services = buildServices(now);
        serviceRepo.saveAll(services);

        List<Student> students = buildStudents(now, rng);
        studentRepo.saveAll(students);

        List<Booking> bookings = buildBookings(students, tutors, services, now, rng);
        bookingRepo.saveAll(bookings);

        List<Invoice> invoices = buildInvoices(students, now, rng);
        invoiceRepo.saveAll(invoices);

        List<Membership> memberships = buildMemberships(students, now, rng);
        membershipRepo.saveAll(memberships);

        log.info("Seeded mock data: {} tutors, {} services, {} students, {} bookings, "
                        + "{} invoices, {} memberships",
                tutors.size(), services.size(), students.size(), bookings.size(),
                invoices.size(), memberships.size());
    }

    private List<Tutor> buildTutors(LocalDateTime now) {
        List<Tutor> tutors = new ArrayList<>();
        for (int i = 0; i < TUTOR_NAMES.length; i++) {
            Tutor t = new Tutor();
            t.setId((long) (i + 1));
            t.setName(TUTOR_NAMES[i]);
            t.setEmail(emailFrom(TUTOR_NAMES[i], "vicilearning.ca"));
            t.setPhone(randomPhone(new Random(i)));
            // One tutor inactive, to exercise the active/visibility flag in the UI.
            t.setActive(i != TUTOR_NAMES.length - 1);
            t.setSyncedAt(now);
            tutors.add(t);
        }
        return tutors;
    }

    private List<Service> buildServices(LocalDateTime now) {
        List<Service> services = new ArrayList<>();
        for (int i = 0; i < SERVICE_DEFS.length; i++) {
            Service s = new Service();
            s.setId((long) (i + 1));
            s.setName((String) SERVICE_DEFS[i][0]);
            s.setDurationMinutes((Integer) SERVICE_DEFS[i][1]);
            s.setCategory((String) SERVICE_DEFS[i][2]);
            s.setLocation((String) SERVICE_DEFS[i][3]);
            s.setActive(true);
            s.setSyncedAt(now);
            services.add(s);
        }
        return services;
    }

    private List<Student> buildStudents(LocalDateTime now, Random rng) {
        List<Student> students = new ArrayList<>();
        // Tracks how many families share each surname, so account keys stay unique (see accountKey).
        Map<String, Integer> surnameUses = new LinkedHashMap<>();
        String siblingLastName = null;      // shared surname of the current family, when applicable
        String siblingAccountKey = null;    // shared Account_ID of the current family
        for (int i = 0; i < studentCount; i++) {
            // ~18% of students (never the first) are siblings: they reuse the previous student's
            // Account_ID and surname, so one family maps to multiple students — the exact shape the
            // Families rollup groups by. The rest each open a new family account.
            boolean sibling = i > 0 && rng.nextDouble() < 0.18;
            String lastName = sibling ? siblingLastName : LAST_NAMES[rng.nextInt(LAST_NAMES.length)];
            if (!sibling) {
                siblingLastName = lastName;
                siblingAccountKey = accountKey(lastName, surnameUses);
            }

            String name = FIRST_NAMES[rng.nextInt(FIRST_NAMES.length)] + " " + lastName;
            Student s = new Student();
            s.setId(STUDENT_ID_BASE + i);
            s.setName(name);
            s.setEmail(emailFrom(name + i, "example.com"));
            s.setPhone(randomPhone(rng));
            // The family key (Account_ID). Mirrors SimplyBook's real `Surname_Account` format
            // (siblings share one); in production it comes from the Account_ID custom field via the
            // REST v2 client. A slice of only-children get this cleared below to seed the Association
            // Account "unassigned" queue.
            s.setAccountId(siblingAccountKey);
            // EXT_ID — the Brevo per-student unique id (one per student, never shared with siblings).
            s.setExtId(String.format("EXT-%05d", 10000 + i));
            s.setCreatedAt(now.minusDays(30 + rng.nextInt(700)));
            s.setSyncedAt(now);
            students.add(s);
        }

        unassignSomeOnlyChildren(students, rng);
        return students;
    }

    /**
     * A unique {@code Surname_Account} family key. First family with a surname gets
     * {@code Tran_Account}; a second, unrelated family with the same surname gets
     * {@code Tran_Account2}, and so on — so two distinct families never collapse into one.
     */
    private String accountKey(String lastName, Map<String, Integer> surnameUses) {
        int n = surnameUses.merge(lastName, 1, Integer::sum);
        return n == 1 ? lastName + "_Account" : lastName + "_Account" + n;
    }

    /**
     * Clears the family (Account_ID) on ~15% of only-children so the Association Account page has
     * an "unassigned students" queue to demo. Only students whose Account_ID is theirs alone are
     * eligible — siblings (a shared Account_ID) are never touched, so no seeded family loses a
     * member. EXT_ID is kept, mirroring a real new Brevo contact that has no family assigned yet.
     */
    private void unassignSomeOnlyChildren(List<Student> students, Random rng) {
        Map<String, Long> perAccount = new LinkedHashMap<>();
        for (Student s : students) {
            if (s.getAccountId() != null) {
                perAccount.merge(s.getAccountId(), 1L, Long::sum);
            }
        }
        for (Student s : students) {
            boolean onlyChild = s.getAccountId() != null && perAccount.get(s.getAccountId()) == 1L;
            if (onlyChild && rng.nextDouble() < 0.15) {
                s.setAccountId(null);
            }
        }
    }

    private List<Booking> buildBookings(List<Student> students, List<Tutor> tutors,
                                        List<Service> services, LocalDateTime now, Random rng) {
        List<Booking> bookings = new ArrayList<>();
        LocalDate windowStart = now.minusDays(LOOKBACK_DAYS).toLocalDate();
        LocalDate windowEnd   = now.plusDays(LOOKAHEAD_DAYS).toLocalDate();
        LocalDate lapsedCutoff = now.minusDays(21).toLocalDate();
        long bookingId = 1;

        for (Student student : students) {
            // ~15% of students are "lapsed": they have history but nothing in the last 3 weeks
            // or upcoming — the top follow-up target for the future rules engine.
            boolean lapsed = rng.nextDouble() < 0.15;
            int sessionsPerWeek = rng.nextDouble() < 0.3 ? 2 : 1;

            // Each student has a "home" service — a family typically sticks to one mode/location
            // (e.g. Virtual One-on-One). Most students are "pure" (home service only); a minority
            // mix in the occasional other. This keeps each family's Category/Location realistically
            // narrow instead of spanning every service, and makes the category filter discriminate.
            Service primaryService = weightedService(services, rng);
            double varietyRate = rng.nextDouble() < 0.70 ? 0.0 : 0.20;

            for (LocalDate week = windowStart; !week.isAfter(windowEnd); week = week.plusWeeks(1)) {
                if (lapsed && !week.isBefore(lapsedCutoff)) break;      // lapsed: stop before recent weeks
                if (rng.nextDouble() < 0.25) continue;                  // not every week has a booking

                for (int n = 0; n < sessionsPerWeek; n++) {
                    LocalDate day = week.plusDays(rng.nextInt(5));      // Mon–Fri
                    int hour = 15 + rng.nextInt(5);                     // 3pm–7pm
                    LocalDateTime start = day.atTime(hour, 0);
                    if (start.toLocalDate().isBefore(windowStart) || start.toLocalDate().isAfter(windowEnd)) {
                        continue;
                    }
                    // Home service unless this student mixes and variety triggers for this session.
                    Service service = rng.nextDouble() < varietyRate ? weightedService(services, rng) : primaryService;
                    LocalDateTime end = start.plusMinutes(service.getDurationMinutes());

                    Booking b = new Booking();
                    b.setId(bookingId++);
                    b.setStudent(student);
                    // ~5% have no assigned tutor (mirrors real gaps in the data).
                    b.setTutor(rng.nextDouble() < 0.05 ? null : tutors.get(rng.nextInt(tutors.size())));
                    b.setService(service);
                    b.setStartTime(start);
                    b.setEndTime(end);

                    // ~10% cancelled, cancelled a little before the session.
                    if (rng.nextDouble() < 0.10) {
                        b.setStatus("cancelled");
                        b.setCancelledAt(start.minusHours(6 + rng.nextInt(48)));
                    } else {
                        b.setStatus("confirmed");
                    }
                    b.setSyncedAt(now);
                    bookings.add(b);
                }
            }
        }
        return bookings;
    }

    /**
     * One or two invoices per student, so the overview cash-flow section and pending-invoices
     * table have data. ~40% are left unpaid (status "pending") — the actionable rows Sara wants
     * visible — the rest are "paid". Amounts are a mock function of a small session block; real
     * amounts come from SimplyBook REST v2.
     */
    private List<Invoice> buildInvoices(List<Student> students, LocalDateTime now, Random rng) {
        List<Invoice> invoices = new ArrayList<>();
        long invoiceId = INVOICE_ID_BASE;
        int number = 1;

        for (Student student : students) {
            int count = 1 + (rng.nextDouble() < 0.35 ? 1 : 0);   // most families have one open cycle, some two
            for (int n = 0; n < count; n++) {
                int sessions = 4 + rng.nextInt(9);               // a 4–12 session block
                boolean paid = rng.nextDouble() >= 0.40;         // ~40% still outstanding

                Invoice inv = new Invoice();
                inv.setId(invoiceId++);
                inv.setStudent(student);
                inv.setNumber(String.format("INV-2026-%04d", number++));
                inv.setStatus(paid ? "paid" : "pending");
                inv.setAmount(SESSION_PRICE.multiply(BigDecimal.valueOf(sessions)));
                inv.setCurrency("CAD");
                // Issued over the last ~10 weeks so "oldest first" ordering on the overview varies.
                inv.setIssuedAt(now.minusDays(3 + rng.nextInt(70)));
                inv.setSyncedAt(now);
                invoices.add(inv);
            }
        }
        return invoices;
    }

    /**
     * One membership per family account (students sharing an {@code accountId} get one shared
     * membership, attached to the first student in the account). Mostly active; a few paused, and
     * a couple near/at zero remaining sessions — the "can't book at 0" families a future rule
     * could surface. Not yet shown in the UI, but seeded so sync-status counts and any future
     * membership view have realistic data.
     */
    private List<Membership> buildMemberships(List<Student> students, LocalDateTime now, Random rng) {
        List<Membership> memberships = new ArrayList<>();
        long membershipId = MEMBERSHIP_ID_BASE;

        // First student encountered per account owns the family membership.
        Map<String, Student> ownerByAccount = new LinkedHashMap<>();
        for (Student s : students) {
            if (s.getAccountId() != null) {
                ownerByAccount.putIfAbsent(s.getAccountId(), s);
            }
        }

        for (Student owner : ownerByAccount.values()) {
            boolean active = rng.nextDouble() >= 0.15;           // ~15% paused
            Membership m = new Membership();
            m.setId(membershipId++);
            m.setStudent(owner);
            m.setName("Prepaid Session Package");
            m.setActive(active);
            // 0–20 remaining; a handful land at 0 (the blocked-from-booking case).
            m.setRemainingCount(rng.nextInt(21));
            m.setStartDate(now.minusDays(30 + rng.nextInt(300)));
            m.setEndDate(now.plusDays(30 + rng.nextInt(120)));
            m.setSyncedAt(now);
            memberships.add(m);
        }
        return memberships;
    }

    // Heavier weighting toward the 1hr Private 1:1 services, like a real tutoring schedule. Indices
    // match SERVICE_DEFS order, so every category/location appears in the booking data (for the
    // filters). Assessments are rarer (a periodic checkpoint, not a weekly session).
    private Service weightedService(List<Service> services, Random rng) {
        double r = rng.nextDouble();
        if (r < 0.38) return services.get(0);   // Private 1:1, in-person 1hr (Centre)
        if (r < 0.60) return services.get(1);   // Private 1:1, virtual 1hr
        if (r < 0.73) return services.get(2);   // Private 1:1, at-home 1hr
        if (r < 0.85) return services.get(3);   // Study Club 1.5hr
        if (r < 0.93) return services.get(4);   // Private 1:1, 2hr intensive (Centre)
        return services.get(5);                 // Assessment 1hr (Centre)
    }

    private String emailFrom(String name, String domain) {
        String local = name.toLowerCase().replaceAll("[^a-z0-9]", ".");
        return local + "@" + domain;
    }

    private String randomPhone(Random rng) {
        return String.format("604-555-%04d", rng.nextInt(10000));
    }
}
