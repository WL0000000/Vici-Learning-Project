package ca.vicilearning.dashboard.sync;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

final class AdapterUtils {

    private static final DateTimeFormatter SB_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SB_TIME     = DateTimeFormatter.ofPattern("HH:mm:ss");

    private AdapterUtils() {}

    // Handles array, id-keyed object {id: {...}}, and paginated {data: [...]}
    static List<JsonNode> asList(JsonNode result) {
        if (result == null || result.isNull()) return List.of();
        if (result.isObject() && result.has("data")) return asList(result.get("data"));
        List<JsonNode> out = new ArrayList<>();
        if (result.isArray()) {
            result.forEach(out::add);
        } else if (result.isObject()) {
            result.properties().forEach(e -> out.add(e.getValue()));
        }
        return out;
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // Parses "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-dd"
    static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, SB_DATETIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    // SimplyBook.me often stores start/end as separate date + time strings
    static LocalDateTime parseDateAndTime(String date, String time) {
        if (date == null || date.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(date);
            LocalTime t = (time != null && !time.isBlank())
                    ? LocalTime.parse(time, SB_TIME)
                    : LocalTime.MIDNIGHT;
            return LocalDateTime.of(d, t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // "0"/"1" or actual boolean
    static boolean parseBool(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        return "1".equals(node.asText());
    }
}
