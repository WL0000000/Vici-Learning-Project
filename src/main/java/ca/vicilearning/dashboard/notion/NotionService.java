package ca.vicilearning.dashboard.notion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class NotionService {

    private static final String NOTION_VERSION = "2026-03-11";

    private final NotionProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private volatile String resolvedTutorsDataSourceId;

    public NotionService(NotionProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = props;
        this.restClient = builder.build();
        this.mapper = mapper;
    }

    public String getTutors() {
        String id = tutorsDataSourceId();
        try {
            return queryDataSource(id);
        } catch (HttpClientErrorException.NotFound e) {
            resolvedTutorsDataSourceId = resolveTutorsDataSourceId(id);
            return queryDataSource(resolvedTutorsDataSourceId);
        }
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

            tutors.sort(Comparator
                    .comparingInt((NotionTutor tutor) -> statusRank(tutor.status()))
                    .thenComparing(NotionTutor::name, String.CASE_INSENSITIVE_ORDER));

            return tutors;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Notion tutors response", e);
        }
    }

    public void updateTutor(String pageId, Map<String, String> formValues) {
        JsonNode currentPage = retrievePage(pageId);
        JsonNode currentProperties = currentPage.path("properties");
        ObjectNode updatedProperties = mapper.createObjectNode();

        putUpdate(updatedProperties, currentProperties, formValues, "name",
                "Tutor Name", "Name", "Tutor", "Full Name");
        putUpdate(updatedProperties, currentProperties, formValues, "tutorId", "Tutor ID");
        putUpdate(updatedProperties, currentProperties, formValues, "email", "Tutor Email", "Email");
        putUpdate(updatedProperties, currentProperties, formValues, "phone", "Tutor Phone", "Phone");
        putUpdate(updatedProperties, currentProperties, formValues, "city", "City");
        putUpdate(updatedProperties, currentProperties, formValues, "postalCode", "Postal Code");
        putUpdate(updatedProperties, currentProperties, formValues, "streetAddress", "Street Address");
        putUpdate(updatedProperties, currentProperties, formValues, "oneSentenceBio",
                "One Sentence Bio", "One Sentence", "Bio");
        putUpdate(updatedProperties, currentProperties, formValues, "bookingLink", "Booking Link", "Booking URL");
        putUpdate(updatedProperties, currentProperties, formValues, "atHomeTutoring", "At-Home Tutoring", "At-Home");
        putUpdate(updatedProperties, currentProperties, formValues, "centreTutoring",
                "Centre Tutoring", "Center Tutoring");
        putUpdate(updatedProperties, currentProperties, formValues, "virtualTutoring", "Virtual Tutoring", "Virtual");
        putUpdate(updatedProperties, currentProperties, formValues, "languages", "Languages");
        putUpdate(updatedProperties, currentProperties, formValues, "subjects", "Subjects");
        putUpdate(updatedProperties, currentProperties, formValues, "startDate", "Start Date");
        putUpdate(updatedProperties, currentProperties, formValues, "endDate", "End Date");
        putUpdate(updatedProperties, currentProperties, formValues, "status", "Status");
        putUpdate(updatedProperties, currentProperties, formValues, "atHomeRate",
                "At-Home Pay Rate", "At-Home Rate");
        putUpdate(updatedProperties, currentProperties, formValues, "virtualCentreRate",
                "Virtual/Centre Pay Rate", "Virtual/Centre Rate");
        putUpdate(updatedProperties, currentProperties, formValues, "supportPayRate",
                "Support Pay Rate", "Support Pay");
        putUpdate(updatedProperties, currentProperties, formValues, "viciRole", "Vici Role");
        putUpdate(updatedProperties, currentProperties, formValues, "tutorProfile",
                "Tutor Profile", "Tutor Profile Link");
        putUpdate(updatedProperties, currentProperties, formValues, "tutorRole", "Tutor Role");
        putUpdate(updatedProperties, currentProperties, formValues, "adminNotes", "Admin Notes", "Admin Note");

        if (updatedProperties.isEmpty()) {
            return;
        }

        ObjectNode body = mapper.createObjectNode();
        body.set("properties", updatedProperties);

        restClient.patch()
                .uri(props.apiBaseUrl() + "/v1/pages/" + pageId)
                .header("Authorization", "Bearer " + props.token())
                .header("Notion-Version", NOTION_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("Notion update failed with status " + response.getStatusCode());
                })
                .body(String.class);
    }

    private String queryDataSource(String dataSourceId) {
        return restClient.post()
                .uri(props.apiBaseUrl() + "/v1/data_sources/" + dataSourceId + "/query")
                .header("Authorization", "Bearer " + props.token())
                .header("Notion-Version", NOTION_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(String.class);
    }

    private String tutorsDataSourceId() {
        if (resolvedTutorsDataSourceId != null && !resolvedTutorsDataSourceId.isBlank()) {
            return resolvedTutorsDataSourceId;
        }
        if (props.tutorsDataSourceId() != null && !props.tutorsDataSourceId().isBlank()) {
            return props.tutorsDataSourceId();
        }
        return resolveTutorsDataSourceId(props.tutorsDatabaseId());
    }

    private String resolveTutorsDataSourceId(String databaseId) {
        if (databaseId == null || databaseId.isBlank()) {
            throw new IllegalStateException("Set NOTION_TUTORS_DATA_SOURCE_ID or NOTION_TUTORS_DATABASE_ID.");
        }

        String rawJson = restClient.get()
                .uri(props.apiBaseUrl() + "/v1/databases/" + databaseId)
                .header("Authorization", "Bearer " + props.token())
                .header("Notion-Version", NOTION_VERSION)
                .retrieve()
                .body(String.class);

        try {
            String dataSourceId = mapper.readTree(rawJson)
                    .path("data_sources")
                    .path(0)
                    .path("id")
                    .asText("");
            if (dataSourceId.isBlank()) {
                throw new IllegalStateException("Notion database has no data sources: " + databaseId);
            }
            return dataSourceId;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Notion database response", e);
        }
    }

    private int statusRank(String status) {
        if ("Active".equalsIgnoreCase(status)) {
            return 0;
        }
        if ("Not started".equalsIgnoreCase(status)) {
            return 1;
        }
        if ("Inactive".equalsIgnoreCase(status)) {
            return 2;
        }
        return 3;
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

    private JsonNode retrievePage(String pageId) {
        String rawJson = restClient.get()
                .uri(props.apiBaseUrl() + "/v1/pages/" + pageId)
                .header("Authorization", "Bearer " + props.token())
                .header("Notion-Version", NOTION_VERSION)
                .retrieve()
                .body(String.class);
        try {
            return mapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Notion page response", e);
        }
    }

    private void putUpdate(ObjectNode updatedProperties,
                           JsonNode currentProperties,
                           Map<String, String> formValues,
                           String formField,
                           String... propertyNames) {
        if (!formValues.containsKey(formField)) {
            return;
        }

        PropertyMatch match = findProperty(currentProperties, propertyNames);
        if (match == null) {
            return;
        }

        JsonNode value = writeValue(match.property(), formValues.getOrDefault(formField, ""));
        if (value != null) {
            updatedProperties.set(match.name(), value);
        }
    }

    private PropertyMatch findProperty(JsonNode properties, String... propertyNames) {
        for (String propertyName : propertyNames) {
            JsonNode property = properties.path(propertyName);
            if (!property.isMissingNode() && !property.isNull()) {
                return new PropertyMatch(propertyName, property);
            }
        }
        return null;
    }

    private JsonNode writeValue(JsonNode currentProperty, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        String type = currentProperty.path("type").asText("");
        ObjectNode property = mapper.createObjectNode();

        switch (type) {
            case "title" -> property.set("title", richTextArray(value));
            case "rich_text" -> property.set("rich_text", richTextArray(value));
            case "email" -> property.set("email", nullableText(value));
            case "phone_number" -> property.set("phone_number", nullableText(value));
            case "url" -> property.set("url", nullableText(value));
            case "number" -> property.set("number", numberValue(value));
            case "status" -> {
                if (value.isBlank()) {
                    return null;
                }
                property.set("status", nameObject(value));
            }
            case "select" -> property.set("select", value.isBlank() ? null : nameObject(value));
            case "multi_select" -> property.set("multi_select", multiSelect(value));
            case "date" -> property.set("date", dateValue(value));
            case "checkbox" -> property.put("checkbox", Boolean.parseBoolean(value));
            default -> {
                return null;
            }
        }

        return property;
    }

    private ArrayNode richTextArray(String value) {
        ArrayNode values = mapper.createArrayNode();
        if (!value.isBlank()) {
            ObjectNode richText = mapper.createObjectNode();
            ObjectNode text = mapper.createObjectNode();
            text.put("content", value);
            richText.set("text", text);
            values.add(richText);
        }
        return values;
    }

    private JsonNode nullableText(String value) {
        return value.isBlank() ? mapper.nullNode() : mapper.getNodeFactory().textNode(value);
    }

    private JsonNode numberValue(String value) {
        if (value.isBlank()) {
            return mapper.nullNode();
        }
        String normalized = value.replace("$", "").replace(",", "").trim();
        try {
            return mapper.getNodeFactory().numberNode(Double.parseDouble(normalized));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number but got \"" + value + "\"");
        }
    }

    private ObjectNode nameObject(String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", value);
        return node;
    }

    private ArrayNode multiSelect(String value) {
        ArrayNode options = mapper.createArrayNode();
        for (String item : value.split(",")) {
            String name = item.trim();
            if (!name.isBlank()) {
                options.add(nameObject(name));
            }
        }
        return options;
    }

    private JsonNode dateValue(String value) {
        if (value.isBlank()) {
            return mapper.nullNode();
        }

        String[] dates = value.split("\\s+-\\s+", 2);
        ObjectNode date = mapper.createObjectNode();
        date.put("start", dates[0].trim());
        if (dates.length > 1 && !dates[1].trim().isBlank()) {
            date.put("end", dates[1].trim());
        }
        return date;
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
            JsonNode properties = retrievePage(pageId).path("properties");
            for (JsonNode property : properties) {
                if ("title".equals(property.path("type").asText(""))) {
                    String title = textArray(property.path("title"));
                    if (!title.isBlank()) {
                        return title;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // If the integration cannot read a related page, keep the relation visible as its id.
        }
        return pageId;
    }

    private record PropertyMatch(String name, JsonNode property) {}
}
