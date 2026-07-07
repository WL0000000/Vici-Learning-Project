package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.Membership;
import ca.vicilearning.dashboard.domain.Student;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Maps SimplyBook.me REST v2 membership JSON into {@link Membership} entities. Like
 * {@link InvoiceAdapter}, parses defensively across the field names the memberships endpoint
 * has used (title/name, the remaining-visits balance, start/end dates).
 */
@Component
public class MembershipAdapter {

    public List<Membership> toMemberships(JsonNode result, Map<Long, Student> students) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return AdapterUtils.asList(result).stream()
                .map(node -> toMembership(node, students, now))
                .filter(m -> m != null)
                .toList();
    }

    private Membership toMembership(JsonNode node, Map<Long, Student> students, LocalDateTime now) {
        long id = node.path("id").asLong();
        if (id == 0) return null;

        Membership m = new Membership();
        m.setId(id);
        m.setStudent(students.get(resolveClientId(node)));
        m.setName(AdapterUtils.blankToNull(firstNonBlank(node, "name", "title", "membership_name")));
        m.setActive(resolveActive(node));
        m.setRemainingCount(resolveRemaining(node));
        m.setStartDate(AdapterUtils.parseDateTime(firstNonBlank(node, "start_date", "date_start")));
        m.setEndDate(AdapterUtils.parseDateTime(firstNonBlank(node, "end_date", "date_end")));
        m.setSyncedAt(now);
        return m;
    }

    private long resolveClientId(JsonNode node) {
        if (node.has("client_id")) return node.path("client_id").asLong();
        return node.path("client").path("id").asLong();
    }

    // Defaults to active when upstream omits the flag: a membership we can see but whose state
    // is unstated is safer treated as live than silently ignored by any downstream alerting.
    private boolean resolveActive(JsonNode node) {
        if (node.has("is_active")) return AdapterUtils.parseBool(node.path("is_active"));
        if (node.has("active"))    return AdapterUtils.parseBool(node.path("active"));
        return true;
    }

    // Remaining balance under whichever name the endpoint uses; null when none is present.
    // Provisional: under a status/renewal membership model this is expected to be null throughout.
    private Integer resolveRemaining(JsonNode node) {
        for (String field : new String[]{"remaining", "visits_remaining", "count", "left"}) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && value.asText("").matches("-?\\d+")) {
                return value.asInt();
            }
        }
        return null;
    }

    private String firstNonBlank(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
