package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.Service;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.Tutor;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Component
public class BookingAdapter {

    private static final Logger log = LoggerFactory.getLogger(BookingAdapter.class);

    public List<Booking> toBookings(JsonNode result,
                                    Map<Long, Student> students,
                                    Map<Long, Tutor> tutors,
                                    Map<Long, Service> services) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return AdapterUtils.asList(result).stream()
                .map(node -> toBooking(node, students, tutors, services, now))
                .filter(b -> b != null)
                .toList();
    }

    private Booking toBooking(JsonNode node,
                               Map<Long, Student> students,
                               Map<Long, Tutor> tutors,
                               Map<Long, Service> services,
                               LocalDateTime now) {
        long id = node.path("id").asLong();
        if (id == 0) return null;

        long clientId  = node.path("client_id").asLong();
        // Admin API names services "events" (event_id); fall back to service_id for older shapes
        long serviceId = node.has("event_id") ? node.path("event_id").asLong()
                                              : node.path("service_id").asLong();

        Student student = students.get(clientId);
        Service service = services.get(serviceId);

        if (student == null || service == null) {
            log.warn("Skipping booking {}: missing student {} or service {}", id, clientId, serviceId);
            return null;
        }

        // Admin API names providers "units" (unit_id); fall back to provider_id for older shapes
        long providerId = node.has("unit_id") ? node.path("unit_id").asLong()
                                             : node.path("provider_id").asLong();
        Tutor tutor = providerId > 0 ? tutors.get(providerId) : null;

        Booking b = new Booking();
        b.setId(id);
        b.setStudent(student);
        b.setTutor(tutor);
        b.setService(service);
        b.setStartTime(resolveStartTime(node));
        b.setEndTime(resolveEndTime(node));
        b.setStatus(resolveStatus(node));
        b.setCancelledAt(AdapterUtils.parseDateTime(node.path("cancel_date").asText(null)));
        b.setSyncedAt(now);
        return b;
    }

    // Status flag: the real admin getBookings response uses "is_confirm" (no "ed"); other/older
    // shapes use "is_confirmed" or a string "status". A set "cancel_date" means the booking was
    // cancelled, regardless of the confirm flag. (Confirmed against live admin API 2026-07-20:
    // the field is "is_confirm" — reading only "is_confirmed" defaulted every booking to confirmed.)
    private String resolveStatus(JsonNode node) {
        if (AdapterUtils.blankToNull(node.path("cancel_date").asText(null)) != null) {
            return "cancelled";
        }
        JsonNode confirm = node.has("is_confirm") ? node.path("is_confirm")
                : node.has("is_confirmed") ? node.path("is_confirmed") : null;
        if (confirm != null) {
            return AdapterUtils.parseBool(confirm) ? "confirmed" : "pending";
        }
        return node.path("status").asText("confirmed");
    }

    // Start time across the SimplyBook shapes: a combined "start_date_time"; a date-only
    // "start_date" plus a separate "start_time"; OR (the real admin getBookings, confirmed
    // 2026-07-20) a "start_date" that already holds the full datetime "yyyy-MM-dd HH:mm:ss".
    // parseDateTime handles that last case (and a bare date), so routing "start_date" straight to
    // parseDateAndTime — which does LocalDate.parse and threw on the datetime string, yielding a
    // null start time for every real booking — is the bug this guards against.
    private LocalDateTime resolveStartTime(JsonNode node) {
        String combined = node.path("start_date_time").asText(null);
        if (combined != null && !combined.isBlank()) return AdapterUtils.parseDateTime(combined);
        String date = node.path("start_date").asText(null);
        String time = node.path("start_time").asText(null);
        if (time != null && !time.isBlank()) return AdapterUtils.parseDateAndTime(date, time);
        return AdapterUtils.parseDateTime(date);
    }

    private LocalDateTime resolveEndTime(JsonNode node) {
        String combined = node.path("end_date_time").asText(null);
        if (combined != null && !combined.isBlank()) return AdapterUtils.parseDateTime(combined);
        String date = node.path("end_date").asText(null);
        String time = node.path("end_time").asText(null);
        if (time != null && !time.isBlank()) return AdapterUtils.parseDateAndTime(date, time);
        return AdapterUtils.parseDateTime(date);
    }
}
