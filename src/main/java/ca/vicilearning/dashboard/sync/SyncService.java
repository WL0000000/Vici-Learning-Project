package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final int BOOKING_LOOKBACK_DAYS  = 90;
    private static final int BOOKING_LOOKAHEAD_DAYS = 30;

    private final SimplybookClient        client;
    private final ClientAdapter           clientAdapter;
    private final PerformerAdapter        performerAdapter;
    private final ServiceAdapter          serviceAdapter;
    private final BookingAdapter          bookingAdapter;
    private final StudentRepository       studentRepo;
    private final TutorRepository         tutorRepo;
    private final ServiceRepository       serviceRepo;
    private final BookingRepository       bookingRepo;
    private final SyncLogRepository       syncLogRepo;

    public SyncService(SimplybookClient client,
                       ClientAdapter clientAdapter,
                       PerformerAdapter performerAdapter,
                       ServiceAdapter serviceAdapter,
                       BookingAdapter bookingAdapter,
                       StudentRepository studentRepo,
                       TutorRepository tutorRepo,
                       ServiceRepository serviceRepo,
                       BookingRepository bookingRepo,
                       SyncLogRepository syncLogRepo) {
        this.client          = client;
        this.clientAdapter   = clientAdapter;
        this.performerAdapter = performerAdapter;
        this.serviceAdapter  = serviceAdapter;
        this.bookingAdapter  = bookingAdapter;
        this.studentRepo     = studentRepo;
        this.tutorRepo       = tutorRepo;
        this.serviceRepo     = serviceRepo;
        this.bookingRepo     = bookingRepo;
        this.syncLogRepo     = syncLogRepo;
    }

    public SyncLog sync() {
        SyncLog entry = new SyncLog();
        entry.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setSuccess(false);
        syncLogRepo.save(entry);

        log.info("Starting SimplyBook.me sync");
        try {
            syncTutors(entry);
            syncServices(entry);
            syncStudents(entry);
            syncBookings(entry);
            entry.setSuccess(true);
            log.info("Sync completed: tutors={} services={} students={} bookings={}",
                    entry.getTutorsUpserted(), entry.getServicesUpserted(),
                    entry.getStudentsUpserted(), entry.getBookingsUpserted());
        } catch (Exception e) {
            log.error("Sync failed: {}", e.getMessage(), e);
            entry.setErrorMessage(e.getMessage());
        } finally {
            entry.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            syncLogRepo.save(entry);
        }
        return entry;
    }

    private void syncTutors(SyncLog entry) {
        List<Tutor> tutors = performerAdapter.toTutors(client.getPerformerList());
        tutorRepo.saveAll(tutors);
        entry.setTutorsUpserted(tutors.size());
    }

    private void syncServices(SyncLog entry) {
        List<ca.vicilearning.dashboard.domain.Service> services =
                serviceAdapter.toServices(client.getServiceList());
        serviceRepo.saveAll(services);
        entry.setServicesUpserted(services.size());
    }

    private void syncStudents(SyncLog entry) {
        List<Student> students = clientAdapter.toStudents(client.getClientList());
        studentRepo.saveAll(students);
        entry.setStudentsUpserted(students.size());
    }

    private void syncBookings(SyncLog entry) {
        Map<Long, Student> studentMap = toMap(studentRepo.findAll(), Student::getId);
        Map<Long, Tutor>   tutorMap   = toMap(tutorRepo.findAll(),   Tutor::getId);
        Map<Long, ca.vicilearning.dashboard.domain.Service> serviceMap =
                toMap(serviceRepo.findAll(), ca.vicilearning.dashboard.domain.Service::getId);

        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(BOOKING_LOOKBACK_DAYS);
        LocalDate to   = LocalDate.now(ZoneOffset.UTC).plusDays(BOOKING_LOOKAHEAD_DAYS);

        List<Booking> bookings = bookingAdapter.toBookings(
                client.getBookingList(from, to), studentMap, tutorMap, serviceMap);
        bookingRepo.saveAll(bookings);
        entry.setBookingsUpserted(bookings.size());
    }

    private <T, K> Map<K, T> toMap(List<T> list, java.util.function.Function<T, K> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, t -> t));
    }
}
