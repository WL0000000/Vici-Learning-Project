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

    // Admin API returns is_confirmed (0/1); older shapes use a string "status"
    private String resolveStatus(JsonNode node) {
        if (node.has("is_confirmed")) {
            return AdapterUtils.parseBool(node.path("is_confirmed")) ? "confirmed" : "pending";
        }
        return node.path("status").asText("confirmed");
    }

    // SimplyBook.me may give start_date + start_time or start_date_time
    private LocalDateTime resolveStartTime(JsonNode node) {
        String combined = node.path("start_date_time").asText(null);
        if (combined != null && !combined.isBlank()) return AdapterUtils.parseDateTime(combined);
        return AdapterUtils.parseDateAndTime(
                node.path("start_date").asText(null),
                node.path("start_time").asText(null));
    }

    private LocalDateTime resolveEndTime(JsonNode node) {
        String combined = node.path("end_date_time").asText(null);
        if (combined != null && !combined.isBlank()) return AdapterUtils.parseDateTime(combined);
        return AdapterUtils.parseDateAndTime(
                node.path("end_date").asText(null),
                node.path("end_time").asText(null));
    }
}
