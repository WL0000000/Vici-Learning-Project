package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.Student;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class ClientAdapter {

    public List<Student> toStudents(JsonNode result) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return AdapterUtils.asList(result).stream()
                .map(node -> toStudent(node, now))
                .filter(s -> s != null)
                .toList();
    }

    private Student toStudent(JsonNode node, LocalDateTime now) {
        long id = node.path("id").asLong();
        if (id == 0) return null;

        Student s = new Student();
        s.setId(id);
        s.setName(node.path("name").asText("Unknown"));
        s.setEmail(AdapterUtils.blankToNull(node.path("email").asText(null)));
        s.setPhone(AdapterUtils.blankToNull(node.path("phone").asText(null)));
        s.setCreatedAt(AdapterUtils.parseDateTime(node.path("created_date").asText(null)));
        s.setSyncedAt(now);
        return s;
    }
}
