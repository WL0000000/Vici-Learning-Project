package ca.vicilearning.dashboard.comms;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the contact readers page past the first page instead of silently stopping at one page
 * (the old {@code /contacts?limit=100&offset=0} cap). Page size is forced to 2 so two short pages
 * exercise the pagination without needing a full page of stub data.
 */
class BrevoCommunicationServiceTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private BrevoCommunicationService service;

    @BeforeEach
    void setUp() {
        RestClient client = RestClient.builder().baseUrl(wm.baseUrl()).build();
        service = new BrevoCommunicationService(client, 2); // page size 2 → forces a second page
    }

    @Test
    void fetchEmailToExtIdMap_readsContactsFromEveryPage() {
        // Page 1 is full (size == page size) → the fetch must request page 2.
        wm.stubFor(get(urlPathEqualTo("/contacts"))
                .withQueryParam("limit", equalTo("2"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson("""
                        {"contacts":[
                          {"email":"a@x.com","ext_id":"E1"},
                          {"email":"b@x.com","ext_id":"E2"}
                        ]}""")));
        // Page 2 is partial (size < page size) → the fetch stops after it.
        wm.stubFor(get(urlPathEqualTo("/contacts"))
                .withQueryParam("limit", equalTo("2"))
                .withQueryParam("offset", equalTo("2"))
                .willReturn(okJson("""
                        {"contacts":[
                          {"email":"c@x.com","ext_id":"E3"}
                        ]}""")));

        Map<String, String> map = service.fetchEmailToExtIdMap();

        // E3 comes from page 2 — its presence proves we didn't stop at the first page.
        assertThat(map)
                .containsEntry("a@x.com", "E1")
                .containsEntry("b@x.com", "E2")
                .containsEntry("c@x.com", "E3");
        wm.verify(getRequestedFor(urlPathEqualTo("/contacts")).withQueryParam("offset", equalTo("2")));
    }

    @Test
    void fetchEmailToExtIdMap_readsExtIdFromAttribute_preferringItOverTopLevel() {
        // Real Brevo returns EXT_ID as a custom attribute (not the top-level ext_id, which the API
        // never echoes). The attribute must win; a contact with only the attribute still resolves.
        wm.stubFor(get(urlPathEqualTo("/contacts"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson("""
                        {"contacts":[
                          {"email":"a@x.com","ext_id":"TOP","attributes":{"EXT_ID":"ATTR-1"}},
                          {"email":"b@x.com","attributes":{"EXT_ID":"ATTR-2"}}
                        ]}""")));

        Map<String, String> map = service.fetchEmailToExtIdMap();

        assertThat(map)
                .containsEntry("a@x.com", "ATTR-1")   // attribute preferred over top-level "TOP"
                .containsEntry("b@x.com", "ATTR-2");   // resolves from the attribute alone
    }

    @Test
    void fetchEmailToExtIdMap_returnsEmpty_whenBrevoFails() {
        wm.stubFor(get(urlPathEqualTo("/contacts")).willReturn(aResponse().withStatus(401)));

        assertThat(service.fetchEmailToExtIdMap()).isEmpty();
    }

    @Test
    void fetchEmailToStatusMap_readsStudentStatusPerContact_omittingBlanks() {
        wm.stubFor(get(urlPathEqualTo("/contacts"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson("""
                        {"contacts":[
                          {"email":"Active@x.com","attributes":{"STUDENT_STATUS":"Active"}},
                          {"email":"paused@x.com","attributes":{"STUDENT_STATUS":"Paused"}},
                          {"email":"none@x.com","attributes":{"STUDENT_STATUS":""}}
                        ]}""")));

        Map<String, String> map = service.fetchEmailToStatusMap();

        // Email is lower-cased; the blank-status contact is omitted (sync leaves it untouched).
        assertThat(map)
                .containsEntry("active@x.com", "Active")
                .containsEntry("paused@x.com", "Paused")
                .doesNotContainKey("none@x.com");
    }
}
