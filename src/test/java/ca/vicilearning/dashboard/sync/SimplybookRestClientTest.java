package ca.vicilearning.dashboard.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "simplybook.api-v2-url=http://localhost:8090",
        "simplybook.company-login=testco",
        "simplybook.admin-username=admin",
        "simplybook.admin-password=secret",
        // BrevoConfig's api-key has no default, so the app context won't load without it.
        // Supplied here (test-only) so this REST test's @SpringBootTest can start.
        "brevo.api.key=test-key"
})
class SimplybookRestClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().port(8090))
            .build();

    @Autowired
    SimplybookRestClient client;

    @BeforeEach
    void stubAuth() {
        // The client is a Spring singleton, so its cached token would leak between tests
        // and make auth-call counts non-deterministic. Reset it for a clean slate.
        ReflectionTestUtils.setField(client, "token", null);
        wm.stubFor(post(urlEqualTo("/admin/auth"))
                .willReturn(okJson("""
                        {"token":"rest-token","company":"testco","login":"admin"}
                        """)));
    }

    @Test
    void getClientFieldValues_authenticatesThenFetchesWithToken() {
        wm.stubFor(get(urlEqualTo("/admin/clients/field-values/2"))
                .willReturn(okJson("""
                        {"id":2,"fields":[
                          {"id":"name","field":{"id":"name","title":"Name","type":"text"},"value":"Alice"},
                          {"id":"f_acc","field":{"id":"f_acc","title":"Account_ID","type":"text"},"value":"VICI-001"}
                        ]}
                        """)));

        JsonNode result = client.getClientFieldValues(2);

        assertThat(result.path("id").asInt()).isEqualTo(2);
        assertThat(result.path("fields").size()).isEqualTo(2);

        // The auth token and company login must be sent on the data request.
        wm.verify(getRequestedFor(urlEqualTo("/admin/clients/field-values/2"))
                .withHeader("X-Token", equalTo("rest-token"))
                .withHeader("X-Company-Login", equalTo("testco")));
        // Auth body carries company/login/password.
        wm.verify(postRequestedFor(urlEqualTo("/admin/auth"))
                .withRequestBody(matchingJsonPath("$.company", equalTo("testco")))
                .withRequestBody(matchingJsonPath("$.password", equalTo("secret"))));
    }

    @Test
    void unauthorized_dropsTokenReauthenticatesAndRetriesOnce() {
        String scenario = "reauth-rest";

        wm.stubFor(get(urlEqualTo("/admin/clients/fields"))
                .inScenario(scenario).whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(401).withBody("{\"message\":\"expired\"}"))
                .willSetStateTo("ok"));

        wm.stubFor(get(urlEqualTo("/admin/clients/fields"))
                .inScenario(scenario).whenScenarioStateIs("ok")
                .willReturn(okJson("""
                        [{"id":"f_acc","title":"Account_ID","type":"text"}]
                        """)));

        JsonNode result = client.getClientFields();

        assertThat(result.isArray()).isTrue();
        assertThat(result.get(0).path("title").asText()).isEqualTo("Account_ID");

        // Initial auth + one re-auth after the 401, and exactly one retry of the GET.
        wm.verify(2, postRequestedFor(urlEqualTo("/admin/auth")));
        wm.verify(2, getRequestedFor(urlEqualTo("/admin/clients/fields")));
    }

    @Test
    void nonAuthHttpError_propagatesAsSimplybookApiException() {
        wm.stubFor(get(urlEqualTo("/admin/clients/fields"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> client.getClientFields())
                .isInstanceOf(SimplybookApiException.class)
                .hasMessageContaining("500");

        // 500 is not auth-related, so we must NOT retry it.
        wm.verify(1, getRequestedFor(urlEqualTo("/admin/clients/fields")));
    }

    @Test
    void getAllInvoices_walksEveryPageUntilAShortPage() {
        // A full page of 100 followed by a short page (< 100) → stop after page 2, no page 3.
        wm.stubFor(get(urlEqualTo("/admin/invoices?page=1&on_page=100"))
                .willReturn(okJson(pageOf(1, 100))));
        wm.stubFor(get(urlEqualTo("/admin/invoices?page=2&on_page=100"))
                .willReturn(okJson(pageOf(101, 5))));

        JsonNode all = client.getAllInvoices();

        assertThat(all.isArray()).isTrue();
        assertThat(all.size()).isEqualTo(105);          // 100 + 5, flattened across pages
        assertThat(all.get(0).path("id").asInt()).isEqualTo(1);
        assertThat(all.get(104).path("id").asInt()).isEqualTo(105);
        wm.verify(1, getRequestedFor(urlEqualTo("/admin/invoices?page=1&on_page=100")));
        wm.verify(1, getRequestedFor(urlEqualTo("/admin/invoices?page=2&on_page=100")));
        wm.verify(0, getRequestedFor(urlEqualTo("/admin/invoices?page=3&on_page=100")));
    }

    @Test
    void getAllMemberships_singleShortPage_stopsImmediately() {
        wm.stubFor(get(urlEqualTo("/admin/clients/memberships?page=1&on_page=100"))
                .willReturn(okJson(pageOf(1, 2))));

        JsonNode all = client.getAllMemberships();

        assertThat(all.size()).isEqualTo(2);
        wm.verify(1, getRequestedFor(urlEqualTo("/admin/clients/memberships?page=1&on_page=100")));
        wm.verify(0, getRequestedFor(urlEqualTo("/admin/clients/memberships?page=2&on_page=100")));
    }

    // Builds a {"data":[...]} page of `count` rows with sequential ids starting at `startId`.
    private static String pageOf(int startId, int count) {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(startId + i).append('}');
        }
        return sb.append("]}").toString();
    }
}
