package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.Service;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class ServiceAdapter {

    public List<Service> toServices(JsonNode result) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return AdapterUtils.asList(result).stream()
                .map(node -> toService(node, now))
                .filter(s -> s != null)
                .toList();
    }

    private Service toService(JsonNode node, LocalDateTime now) {
        long id = node.path("id").asLong();
        if (id == 0) return null;

        Service s = new Service();
        s.setId(id);
        s.setName(node.path("name").asText("Unknown"));
        s.setDurationMinutes(node.path("duration").asInt(0) == 0
                ? null
                : node.path("duration").asInt());
        // Category ("One-on-One" / "Study Club") and location ("At Home" / "Virtual Tutoring" /
        // "VICI Learning Centre") from the Meeting #3 export. The live JSON field names aren't
        // confirmed yet, so read a few plausible variants defensively and leave null if absent.
        s.setCategory(firstText(node, "category", "category_name", "session_category"));
        s.setLocation(firstText(node, "service_category", "location", "location_name"));
        // Admin API events expose is_active; fall back to is_visible for older shapes
        s.setActive(AdapterUtils.parseBool(
                node.has("is_active") ? node.path("is_active") : node.path("is_visible")));
        s.setSyncedAt(now);
        return s;
    }

    /** First non-blank string among the given field names, or null if none is present. */
    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = AdapterUtils.blankToNull(node.path(field).asText(null));
            if (value != null) return value;
        }
        return null;
    }
}
