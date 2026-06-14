package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.Tutor;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class PerformerAdapter {

    public List<Tutor> toTutors(JsonNode result) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return AdapterUtils.asList(result).stream()
                .map(node -> toTutor(node, now))
                .filter(t -> t != null)
                .toList();
    }

    private Tutor toTutor(JsonNode node, LocalDateTime now) {
        long id = node.path("id").asLong();
        if (id == 0) return null;

        Tutor t = new Tutor();
        t.setId(id);
        t.setName(node.path("name").asText("Unknown"));
        t.setEmail(AdapterUtils.blankToNull(node.path("email").asText(null)));
        t.setPhone(AdapterUtils.blankToNull(node.path("phone").asText(null)));
        t.setActive(AdapterUtils.parseBool(node.path("is_visible")));
        t.setSyncedAt(now);
        return t;
    }
}
