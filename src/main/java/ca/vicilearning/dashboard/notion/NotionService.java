package ca.vicilearning.dashboard.notion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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
            Map<String, String> relationTitleCache = new HashMap<>();

            for (JsonNode page : root.path("results")) {
                JsonNode properties = page.path("properties");
                tutors.add(new NotionTutor(
                        page.path("id").asText(""),
                        firstValue(properties, relationTitleCache, "Tutor Name", "Name", "Tutor", "Full Name"),
                        value(properties, "Tutor ID", relationTitleCache),
                        firstValue(properties, relationTitleCache, "Tutor Email", "Email"),
                        firstValue(properties, relationTitleCache, "Tutor Phone", "Phone"),
                        value(properties, "City", relationTitleCache),
                        value(properties, "Postal Code", relationTitleCache),
                        value(properties, "Street Address", relationTitleCache),
                        firstValue(properties, relationTitleCache, "One Sentence Bio", "One Sentence", "Bio"),
                        firstValue(properties, relationTitleCache, "Booking Link", "Booking URL"),
                        firstValue(properties, relationTitleCache, "At-Home Tutoring", "At-Home"),
                        firstValue(properties, relationTitleCache, "Centre Tutoring", "Centre Tutoring", "Center Tutoring"),
                        firstValue(properties, relationTitleCache, "Virtual Tutoring", "Virtual"),
                        value(properties, "Languages", relationTitleCache),
                        value(properties, "Subjects", relationTitleCache),
                        value(properties, "Start Date", relationTitleCache),
                        value(properties, "End Date", relationTitleCache),
                        value(properties, "Status", relationTitleCache),
                        firstValue(properties, relationTitleCache, "At-Home Pay Rate", "At-Home Rate", "At-Home"),
                        firstValue(properties, relationTitleCache, "Virtual/Centre Pay Rate", "Virtual/Centre Rate", "Virtual/Centre"),
                        firstValue(properties, relationTitleCache, "Support Pay Rate", "Support Pay"),
                        value(properties, "Person", relationTitleCache),
                        value(properties, "Vici Role", relationTitleCache),
                        firstValue(properties, relationTitleCache, "Tutor Profile", "Tutor Profile Link"),
                        value(properties, "Tutor Role", relationTitleCache),
                        firstValue(properties, relationTitleCache, "Assigned Admin", "Assigned"),
                        firstValue(properties, relationTitleCache, "Admin Notes", "Admin Note"),
                        page.path("url").asText("")
                ));
            }

            return tutors;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Notion tutors response", e);
        }
    }

    private String firstValue(JsonNode properties, Map<String, String> relationTitleCache, String... propertyNames) {
        for (String propertyName : propertyNames) {
            String value = value(properties, propertyName, relationTitleCache);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String value(JsonNode properties, String propertyName, Map<String, String> relationTitleCache) {
        JsonNode property = properties.path(propertyName);
        if (property.isMissingNode() || property.isNull()) {
            return "";
        }

        return switch (property.path("type").asText("")) {
            case "title" -> textArray(property.path("title"));
            case "rich_text" -> textArray(property.path("rich_text"));
            case "email" -> property.path("email").asText("");
            case "phone_number" -> property.path("phone_number").asText("");
            case "url" -> property.path("url").asText("");
            case "number" -> property.path("number").isNumber() ? property.path("number").asText() : "";
            case "status" -> property.path("status").path("name").asText("");
            case "select" -> property.path("select").path("name").asText("");
            case "multi_select" -> names(property.path("multi_select"));
            case "date" -> dateRange(property.path("date"));
            case "people" -> people(property.path("people"));
            case "relation" -> relationTitles(property.path("relation"), relationTitleCache);
            case "checkbox" -> property.path("checkbox").isBoolean()
                    ? Boolean.toString(property.path("checkbox").asBoolean()) : "";
            default -> "";
        };
    }

    private String textArray(JsonNode nodes) {
        StringBuilder text = new StringBuilder();
        for (JsonNode node : nodes) {
            text.append(node.path("plain_text").asText(""));
        }
        return text.toString();
    }

    private String names(JsonNode nodes) {
        StringJoiner joiner = new StringJoiner(", ");
        for (JsonNode node : nodes) {
            String name = node.path("name").asText("");
            if (!name.isBlank()) {
                joiner.add(name);
            }
        }
        return joiner.toString();
    }

    private String dateRange(JsonNode date) {
        String start = date.path("start").asText("");
        String end = date.path("end").asText("");
        if (start.isBlank()) {
            return "";
        }
        return end.isBlank() ? start : start + " - " + end;
    }

    private String people(JsonNode nodes) {
        StringJoiner joiner = new StringJoiner(", ");
        for (JsonNode node : nodes) {
            String name = node.path("name").asText("");
            if (!name.isBlank()) {
                joiner.add(name);
            }
        }
        return joiner.toString();
    }

    private String relationTitles(JsonNode nodes, Map<String, String> relationTitleCache) {
        StringJoiner joiner = new StringJoiner(", ");
        for (JsonNode node : nodes) {
            String id = node.path("id").asText("");
            if (!id.isBlank()) {
                joiner.add(relationTitleCache.computeIfAbsent(id, this::pageTitle));
            }
        }
        return joiner.toString();
    }

    private String pageTitle(String pageId) {
        try {
            String rawJson = restClient.get()
                    .uri(props.apiBaseUrl() + "/v1/pages/" + pageId)
                    .header("Authorization", "Bearer " + props.token())
                    .header("Notion-Version", NOTION_VERSION)
                    .retrieve()
                    .body(String.class);
            JsonNode properties = mapper.readTree(rawJson).path("properties");
            for (JsonNode property : properties) {
                if ("title".equals(property.path("type").asText(""))) {
                    String title = textArray(property.path("title"));
                    if (!title.isBlank()) {
                        return title;
                    }
                }
            }
        } catch (RuntimeException | JsonProcessingException ignored) {
            // If the integration cannot read a related page, keep the relation visible as its id.
        }
        return pageId;
    }
}
