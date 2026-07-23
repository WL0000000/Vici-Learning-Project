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
        m.setName(AdapterUtils.blankToNull(resolveName(node)));
        m.setActive(resolveActive(node));
        m.setRemainingCount(resolveRemaining(node));
        m.setStartDate(AdapterUtils.parseDateTime(firstNonBlank(node, "period_start", "start_date", "date_start")));
        m.setEndDate(AdapterUtils.parseDateTime(firstNonBlank(node, "period_end", "end_date", "date_end")));
        m.setSyncedAt(now);
        return m;
    }

    private long resolveClientId(JsonNode node) {
        if (node.has("client_id")) return node.path("client_id").asLong();
        return node.path("client").path("id").asLong();
    }

    // The package name is nested under "membership" in the real REST v2 shape (confirmed against the
    // live account 2026-07-23); older/other shapes put name/title at the top level.
    private String resolveName(JsonNode node) {
        String nested = node.path("membership").path("name").asText(null);
        if (nested != null && !nested.isBlank()) return nested;
        return firstNonBlank(node, "name", "title", "membership_name");
    }

    // Real REST v2 shape (confirmed 2026-07-23) has no is_active/active; it exposes can_be_used
    // (bool), is_expired (bool), and a text status. Prefer those. Defaults to active when nothing
    // is stated, so a membership we can see is never silently ignored by balance alerting.
    private boolean resolveActive(JsonNode node) {
        if (node.has("is_active"))   return AdapterUtils.parseBool(node.path("is_active"));
        if (node.has("active"))      return AdapterUtils.parseBool(node.path("active"));
        if (node.has("can_be_used")) return AdapterUtils.parseBool(node.path("can_be_used"));
        if (node.has("is_expired"))  return !AdapterUtils.parseBool(node.path("is_expired"));
        String status = node.path("status").asText(null);
        if (status != null && !status.isBlank()) {
            String s = status.trim().toLowerCase();
            return !(s.contains("cancel") || s.contains("expire") || s.contains("inactive"));
        }
        return true;
    }

    // Remaining prepaid-session balance. The real REST v2 field is "rest" (confirmed 2026-07-23,
    // matches the memberships-export "rest" column). NB: "count" is the package TOTAL, not the
    // remaining balance, so it is deliberately NOT used here.
    private Integer resolveRemaining(JsonNode node) {
        for (String field : new String[]{"rest", "remaining", "visits_remaining", "left"}) {
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
