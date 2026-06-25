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

    /**
     * Pulls the Account_ID value out of a REST v2 {@code Client_DetailsEntity}
     * ({@code {id, fields:[{id, field:{id,title,type}, value}]}}). We match on the field's
     * {@code title} (case-insensitive) or its id, so the column can be renamed in SimplyBook
     * without breaking us as long as the configured title is updated.
     *
     * @return the trimmed Account_ID, or {@code null} if the field is absent/blank.
     */
    public String extractAccountId(JsonNode clientDetails, String fieldTitle) {
        if (clientDetails == null || fieldTitle == null) return null;
        for (JsonNode f : clientDetails.path("fields")) {
            String title = f.path("field").path("title").asText("");
            String fieldId = f.path("id").asText("");
            if (title.equalsIgnoreCase(fieldTitle) || fieldId.equalsIgnoreCase(fieldTitle)) {
                return AdapterUtils.blankToNull(f.path("value").asText(null));
            }
        }
        return null;
    }
}
