package ca.vicilearning.dashboard.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SimplybookClient {

    private static final String LOGIN_URL = "https://user-api.simplybook.me/login";
    private static final String ADMIN_URL  = "https://user-api.simplybook.me/admin";

    private final SimplybookProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final AtomicLong requestId = new AtomicLong();

    private volatile String adminToken;

    public SimplybookClient(SimplybookProperties props,
                            RestClient.Builder builder,
                            ObjectMapper mapper) {
        this.props = props;
        this.restClient = builder.build();
        this.mapper = mapper;
    }

    // ── Public API methods ────────────────────────────────────────────────────

    public JsonNode getClientList() {
        return adminCall("getClientList", mapper.createArrayNode());
    }

    public JsonNode getServiceList() {
        return adminCall("getServiceList", mapper.createArrayNode());
    }

    public JsonNode getPerformerList() {
        return adminCall("getPerformerList", mapper.createArrayNode());
    }

    public JsonNode getBookingList(LocalDate from, LocalDate to) {
        ObjectNode filter = mapper.createObjectNode()
                .put("date_from", from.toString())
                .put("date_to", to.toString());
        ArrayNode params = mapper.createArrayNode().add(filter);
        return adminCall("getBookingList", params);
    }

    // ── Token management ─────────────────────────────────────────────────────

    private synchronized void ensureAdminToken() {
        if (adminToken == null) {
            ArrayNode params = mapper.createArrayNode()
                    .add(props.companyLogin())
                    .add(props.adminUsername())
                    .add(props.adminPassword());
            adminToken = rpcCall(LOGIN_URL, null, "getUserToken", params).asText();
        }
    }

    private JsonNode adminCall(String method, JsonNode params) {
        ensureAdminToken();
        try {
            return rpcCall(ADMIN_URL, adminToken, method, params);
        } catch (SimplybookApiException e) {
            // -32000 is the generic SimplyBook.me auth error code
            if (e.getCode() == -32000 || e.getCode() == 401) {
                synchronized (this) { adminToken = null; }
                ensureAdminToken();
                return rpcCall(ADMIN_URL, adminToken, method, params);
            }
            throw e;
        }
    }

    // ── Core JSON-RPC transport ───────────────────────────────────────────────

    private JsonNode rpcCall(String url, String token, String method, JsonNode params) {
        ObjectNode body = mapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", requestId.incrementAndGet())
                .put("method", method);
        body.set("params", params);

        String rawBody;
        try {
            rawBody = mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new SimplybookApiException(-1, "Failed to serialize request: " + e.getMessage());
        }

        String raw = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Company-Login", props.companyLogin())
                .headers(h -> { if (token != null) h.set("X-Token", token); })
                .body(rawBody)
                .retrieve()
                .body(String.class);

        if (raw == null || raw.isBlank()) {
            throw new SimplybookApiException(-1, "Empty response from " + url);
        }

        JsonNode response;
        try {
            response = mapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new SimplybookApiException(-1, "Failed to parse response: " + e.getMessage());
        }

        if (response.has("error")) {
            JsonNode error = response.get("error");
            throw new SimplybookApiException(
                    error.path("code").asInt(),
                    error.path("message").asText()
            );
        }

        return response.get("result");
    }
}
