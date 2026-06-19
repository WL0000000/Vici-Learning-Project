package ca.vicilearning.dashboard.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "simplybook.login-url=http://localhost:8089/login",
        "simplybook.admin-url=http://localhost:8089/admin"
})
class SimplybookClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().port(8089))
            .build();

    @Autowired
    SimplybookClient client;

    @BeforeEach
    void resetToken() {
        // Force token re-fetch each test so stubs are exercised predictably
        // (field is package-private via reflection would be cleaner but this works
        //  because each test registers a fresh /login stub)
        wm.stubFor(post(urlEqualTo("/login"))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":1,"result":"test-token"}
                        """)));
    }

    @Test
    void getClientList_parsesObjectFormat() {
        wm.stubFor(post(urlEqualTo("/admin"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("getClientList")))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":2,"result":{
                            "1":{"id":"1","name":"Alice Smith","email":"alice@test.com",
                                 "phone":"604-555-0101","created_date":"2026-01-15 10:00:00"},
                            "2":{"id":"2","name":"Bob Jones","email":"bob@test.com",
                                 "created_date":"2026-02-20"}
                        }}
                        """)));

        JsonNode result = client.getClientList();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get("1").get("name").asText()).isEqualTo("Alice Smith");
        assertThat(result.get("2").get("email").asText()).isEqualTo("bob@test.com");
    }

    @Test
    void getPerformerList_parsesObjectFormat() {
        wm.stubFor(post(urlEqualTo("/admin"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("getUnitList")))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":3,"result":{
                            "1":{"id":"1","name":"Tutor Alice","email":"alice@vici.com","is_visible":"1"},
                            "2":{"id":"2","name":"Tutor Bob","email":"bob@vici.com","is_visible":"0"}
                        }}
                        """)));

        JsonNode result = client.getPerformerList();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get("1").get("name").asText()).isEqualTo("Tutor Alice");
        assertThat(result.get("2").get("is_visible").asText()).isEqualTo("0");
    }

    @Test
    void getServiceList_parsesObjectFormat() {
        wm.stubFor(post(urlEqualTo("/admin"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("getEventList")))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":4,"result":{
                            "1":{"id":"1","name":"Math Tutoring","duration":"60","is_visible":"1"},
                            "2":{"id":"2","name":"English Tutoring","duration":"90","is_visible":"1"}
                        }}
                        """)));

        JsonNode result = client.getServiceList();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get("1").get("duration").asInt()).isEqualTo(60);
    }

    @Test
    void getBookingList_sendsDateRangeInParams() {
        wm.stubFor(post(urlEqualTo("/admin"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("getBookings")))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":5,"result":{
                            "101":{"id":"101","client_id":"1","provider_id":"1","service_id":"1",
                                   "start_date":"2026-06-15","start_time":"10:00:00",
                                   "end_date":"2026-06-15","end_time":"11:00:00",
                                   "status":"confirmed","cancel_date":null}
                        }}
                        """)));

        JsonNode result = client.getBookingList(LocalDate.of(2026, 3, 16), LocalDate.of(2026, 6, 15));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get("101").get("status").asText()).isEqualTo("confirmed");

        // Verify date filter was sent in the request body
        wm.verify(postRequestedFor(urlEqualTo("/admin"))
                .withRequestBody(matchingJsonPath("$.params[0].date_from", equalTo("2026-03-16")))
                .withRequestBody(matchingJsonPath("$.params[0].date_to",   equalTo("2026-06-15"))));
    }

    @Test
    void authError_dropsTokenReauthenticatesAndRetriesOnce() {
        String scenario = "reauth";

        // First /admin call comes back with the SimplyBook auth error code -32000…
        wm.stubFor(post(urlEqualTo("/admin"))
                .inScenario(scenario).whenScenarioStateIs(STARTED)
                .withRequestBody(matchingJsonPath("$.method", equalTo("getEventList")))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":2,
                         "error":{"code":-32000,"message":"Token expired"}}
                        """))
                .willSetStateTo("reauthed"));

        // …and the retry (after a fresh login) succeeds.
        wm.stubFor(post(urlEqualTo("/admin"))
                .inScenario(scenario).whenScenarioStateIs("reauthed")
                .withRequestBody(matchingJsonPath("$.method", equalTo("getEventList")))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":3,"result":{
                            "1":{"id":"1","name":"Math Tutoring","duration":"60","is_visible":"1"}
                        }}
                        """)));

        JsonNode result = client.getServiceList();

        // The caller transparently gets the retried result, not the auth error.
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get("1").get("name").asText()).isEqualTo("Math Tutoring");

        // A fresh token was fetched (login called again) and exactly one retry was issued.
        wm.verify(moreThanOrExactly(1), postRequestedFor(urlEqualTo("/login")));
        wm.verify(exactly(2), postRequestedFor(urlEqualTo("/admin")));
    }

    @Test
    void authError_retriesAtMostOnce_thenPropagates() {
        // /admin keeps returning the auth error even after re-login.
        wm.stubFor(post(urlEqualTo("/admin"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("getEventList")))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":2,
                         "error":{"code":-32000,"message":"Token expired"}}
                        """)));

        assertThatThrownBy(() -> client.getServiceList())
                .isInstanceOf(SimplybookApiException.class)
                .hasMessageContaining("-32000");

        // Original call + exactly one retry = 2; the client must not loop forever.
        wm.verify(exactly(2), postRequestedFor(urlEqualTo("/admin")));
    }

    @Test
    void nonAuthApiError_throwsSimplybookApiException() {
        wm.stubFor(post(urlEqualTo("/admin"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("getEventList")))
                .willReturn(okJson("""
                        {"jsonrpc":"2.0","id":4,
                         "error":{"code":-32601,"message":"Method not found"}}
                        """)));

        assertThatThrownBy(() -> client.getServiceList())
                .isInstanceOf(SimplybookApiException.class)
                .hasMessageContaining("-32601")
                .hasMessageContaining("Method not found");
    }
}
