package ca.vicilearning.dashboard.notion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class NotionService {

    private static final String NOTION_VERSION = "2026-03-11";

    private final NotionProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    public NotionService(NotionProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = props;
        this.restClient = builder.build();
        this.mapper = mapper;
    }

    public String getTutors() {
        return restClient.post()
                .uri(props.apiBaseUrl() + "/v1/data_sources/" + props.tutorsDataSourceId() + "/query")
                .header("Authorization", "Bearer " + props.token())
                .header("Notion-Version", NOTION_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(String.class);
    }

    public List<NotionTutor> getTutorRows() {
        String rawJson = getTutors();
        try {
            JsonNode root = mapper.readTree(rawJson);
            List<NotionTutor> tutors = new ArrayList<>();

            for (JsonNode page : root.path("results")) {
                JsonNode properties = page.path("properties");
                tutors.add(new NotionTutor(
                        page.path("id").asText(""),
                        titleText(properties, "Tutor Name"),
                        richText(properties, "Email"),
                        richText(properties, "Subject"),
                        statusText(properties, "Status"),
                        page.path("url").asText("")
                ));
            }

            return tutors;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Notion tutors response", e);
        }
    }

    private String titleText(JsonNode properties, String propertyName) {
        return properties.path(propertyName).path("title").path(0).path("plain_text").asText("");
    }

    private String richText(JsonNode properties, String propertyName) {
        return properties.path(propertyName).path("rich_text").path(0).path("plain_text").asText("");
    }

    private String statusText(JsonNode properties, String propertyName) {
        return properties.path(propertyName).path("status").path("name").asText("");
    }
}
