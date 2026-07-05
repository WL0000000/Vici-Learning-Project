package ca.vicilearning.dashboard.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Client for SimplyBook.me's REST API v2 ({@code user-api-v2.simplybook.me}).
 *
 * <p>We keep this separate from {@link SimplybookClient} (JSON-RPC v1) on purpose: the
 * JSON-RPC API physically cannot return custom client fields, memberships, or invoices —
 * SimplyBook's own support confirmed this and points to REST v2 for that data. The two
 * clients coexist; the JSON-RPC one still owns the bulk booking/client/service sync.
 *
 * <p>Auth differs from v1: {@code POST /admin/auth} with {@code {company, login, password}}
 * returns a token we then send as the {@code X-Token} header (plus {@code X-Company-Login}).
 * On a 401 we drop the token, re-authenticate once, and retry — mirroring the JSON-RPC client.
 */
@Component
public class SimplybookRestClient {

    private final SimplybookProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    private volatile String token;

    // Rows requested per page for paginated endpoints, and a hard cap on pages walked so a
    // misbehaving API (e.g. one that never returns a short final page) can't loop forever.
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 1000;

    public SimplybookRestClient(SimplybookProperties props,
                                RestClient.Builder builder,
                                ObjectMapper mapper) {
        this.props = props;
        this.restClient = builder.build();
        this.mapper = mapper;
    }

    // ── Public API methods ────────────────────────────────────────────────────

    /** GET /admin/clients/field-values/{id} → Client_DetailsEntity {id, fields:[...]}. */
    public JsonNode getClientFieldValues(long clientId) {
        return adminGet("/admin/clients/field-values/" + clientId);
    }

    /** GET /admin/clients/fields → array of client custom-field definitions. */
    public JsonNode getClientFields() {
        return adminGet("/admin/clients/fields");
    }

    /**
     * GET /admin/clients/memberships (paginated) — membership balances behind the
     * "can't book at 0" model. Wired in here so the next phase can consume it directly.
     */
    public JsonNode getMemberships(int page, int onPage) {
        return adminGet("/admin/clients/memberships?page=" + page + "&on_page=" + onPage);
    }

    /**
     * GET /admin/invoices (paginated) — orders/invoices behind the "pending invoices /
     * cash flow" overview ask. Wired in here for the next phase.
     */
    public JsonNode getInvoices(int page, int onPage) {
        return adminGet("/admin/invoices?page=" + page + "&on_page=" + onPage);
    }

    /** Every invoice across all pages, flattened into one array. */
    public ArrayNode getAllInvoices() {
        return fetchAllPages(this::getInvoices);
    }

    /** Every membership across all pages, flattened into one array. */
    public ArrayNode getAllMemberships() {
        return fetchAllPages(this::getMemberships);
    }

    /**
     * Walks a paginated endpoint from page 1 and concatenates every row into one array. Stops
     * when a page returns fewer than {@link #PAGE_SIZE} rows (the last page) or nothing, so it
     * doesn't depend on any particular pagination-metadata field. {@link #MAX_PAGES} bounds the
     * walk defensively. Each page request re-uses {@link #adminGet}, so auth/retry still apply.
     */
    private ArrayNode fetchAllPages(BiFunction<Integer, Integer, JsonNode> pageFetcher) {
        ArrayNode all = mapper.createArrayNode();
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<JsonNode> rows = AdapterUtils.asList(pageFetcher.apply(page, PAGE_SIZE));
            rows.forEach(all::add);
            if (rows.size() < PAGE_SIZE) break;   // short (or empty) page → last page
        }
        return all;
    }

    // ── Token management ─────────────────────────────────────────────────────

    private synchronized void ensureToken() {
        if (token == null) {
            ObjectNode body = mapper.createObjectNode()
                    .put("company", props.companyLogin())
                    .put("login", props.adminUsername())
                    .put("password", props.restAuthSecret());
            JsonNode res = exchange("POST", props.apiV2Url() + "/admin/auth", body, null);
            String fresh = res.path("token").asText(null);
            if (fresh == null || fresh.isBlank()) {
                throw new SimplybookApiException(-1, "REST v2 /admin/auth returned no token");
            }
            token = fresh;
        }
    }

    private JsonNode adminGet(String path) {
        ensureToken();
        try {
            return exchange("GET", props.apiV2Url() + path, null, token);
        } catch (SimplybookApiException e) {
            // 401 = token expired/invalid: drop it, re-auth once, retry exactly once.
            if (e.getCode() == 401) {
                synchronized (this) { token = null; }
                ensureToken();
                return exchange("GET", props.apiV2Url() + path, null, token);
            }
            throw e;
        }
    }

    // ── Core REST transport ───────────────────────────────────────────────────

    private JsonNode exchange(String method, String url, JsonNode body, String tkn) {
        RestClient.RequestBodySpec spec = restClient
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Company-Login", props.companyLogin())
                .headers(h -> { if (tkn != null) h.set("X-Token", tkn); });

        if (body != null) {
            try {
                spec = spec.contentType(MediaType.APPLICATION_JSON)
                           .body(mapper.writeValueAsString(body));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new SimplybookApiException(-1, "Failed to serialize request: " + e.getMessage());
            }
        }

        String raw = spec.retrieve()
                // Surface HTTP errors as SimplybookApiException so adminGet can detect 401
                // and re-auth. Without this, RestClient throws its own exception type.
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new SimplybookApiException(
                            res.getStatusCode().value(),
                            method + " " + url + " failed with HTTP " + res.getStatusCode().value());
                })
                .body(String.class);

        if (raw == null || raw.isBlank()) {
            throw new SimplybookApiException(-1, "Empty response from " + url);
        }
        try {
            return mapper.readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SimplybookApiException(-1, "Failed to parse response from " + url + ": " + e.getMessage());
        }
    }
}
