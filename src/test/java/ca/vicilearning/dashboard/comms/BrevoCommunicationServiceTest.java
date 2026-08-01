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
        service = new BrevoCommunicationService(client, 2, "Student"); // page size 2 forces a 2nd page
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
    void fetchStudents_keepsOnlyStudentContactType_keyedByExtId_withRealShape() {
        // Mirrors Vici's real Brevo (2026-07-30): CONTACT_TYPE/CONTACT_STATUS are lists, EXT_ID is an
        // attribute, phone is SMS, name is STUDENT_NAME. A tutor and an EXT_ID-less contact drop out.
        wm.stubFor(get(urlPathEqualTo("/contacts"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson("""
                        {"contacts":[
                          {"email":"kid@x.com","attributes":{"EXT_ID":"EXT-1","CONTACT_TYPE":["Student"],
                             "CONTACT_STATUS":["Active"],"STUDENT_NAME":"Ashe Collett","SMS":"+1555"}},
                          {"email":"tutor@x.com","attributes":{"EXT_ID":"EXT-9","CONTACT_TYPE":["Tutor"],
                             "CONTACT_STATUS":["Active"]}},
                          {"email":"noext@x.com","attributes":{"CONTACT_TYPE":["Student"]}}
                        ]}""")));

        var students = service.fetchStudents();

        assertThat(students).hasSize(1);
        BrevoCommunicationService.BrevoStudent s = students.get(0);
        assertThat(s.extId()).isEqualTo("EXT-1");
        assertThat(s.name()).isEqualTo("Ashe Collett");
        assertThat(s.email()).isEqualTo("kid@x.com");
        assertThat(s.phone()).isEqualTo("+1555");
        assertThat(s.status()).isEqualTo("Active");   // first element of the CONTACT_STATUS list
    }

    @Test
    void fetchStudents_returnsEmpty_whenBrevoFails() {
        wm.stubFor(get(urlPathEqualTo("/contacts")).willReturn(aResponse().withStatus(401)));

        assertThat(service.fetchStudents()).isEmpty();
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
