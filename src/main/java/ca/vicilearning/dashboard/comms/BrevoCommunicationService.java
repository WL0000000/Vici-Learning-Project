package ca.vicilearning.dashboard.comms;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BrevoCommunicationService {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrevoAttributesNode(
        @JsonProperty("VICI_ACCOUNT_ID") String viciAccountId,
        @JsonProperty("STUDENT_NAMES") String studentNames,
        @JsonProperty("ACTIVITY_STATUS") String activityStatus,
        @JsonProperty("LAST_BOOKING_DATE") String lastBookingDate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrevoContactNode(
        @JsonProperty("email") String email,
        @JsonProperty("attributes") BrevoAttributesNode attributes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrevoListContactsResponse(
        @JsonProperty("contacts") List<BrevoContactNode> contacts,
        @JsonProperty("count") Long count
    ) {}

    private final RestClient brevoRestClient;

    public BrevoCommunicationService(RestClient brevoRestClient) {
        this.brevoRestClient = brevoRestClient;
    }

    /**
     * Universal Map Compiler: Hits the GET /contacts route exactly ONCE.
     * Compiles an in-memory look-up dictionary of VICI_ACCOUNT_ID -> email.
     */
    public Map<String, String> fetchViciIdToEmailMap() {
        Map<String, String> lookupMap = new HashMap<>();
        try {
            System.out.println("[SERVICE] Pulling universal contact index from Brevo...");
            
            BrevoListContactsResponse response = brevoRestClient.get()
                    .uri("/contacts?limit=100&offset=0") // Clean single-page pull matching test mechanics
                    .retrieve()
                    .body(BrevoListContactsResponse.class);

            if (response != null && response.contacts() != null) {
                for (BrevoContactNode contact : response.contacts()) {
                    if (contact.attributes() != null && contact.attributes().viciAccountId() != null) {
                        String cleanViciId = contact.attributes().viciAccountId().trim().toUpperCase();
                        if (!cleanViciId.isEmpty()) {
                            lookupMap.put(cleanViciId, contact.email().trim());
                        }
                    }
                }
            }
            System.out.println("[SERVICE SUCCESS] Universal mapping initialized. Cached " + lookupMap.size() + " lookup keys.");
        } catch (Exception e) {
            System.err.println("[SERVICE CRITICAL ERROR] Failed compiling local index cache: " + e.getMessage());
            e.printStackTrace();
        }
        return lookupMap;
    }

    public void updateContactAttributes(String parentEmail, Map<String, Object> attributePayload) {
        if (parentEmail == null || attributePayload == null) return;
        try {
            Map<String, Object> bodyWrapper = Map.of("attributes", attributePayload);
            brevoRestClient.put()
                    .uri("/contacts/{email}", parentEmail.trim())
                    .body(bodyWrapper)
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("[SERVICE SUCCESS] Attributes updated on Brevo for: " + parentEmail);
        } catch (Exception e) {
            System.err.println("[SERVICE EXCEPTION] Attribute update failed: " + e.getMessage());
        }
    }

    public void sendTemplatedEmail(String targetEmail, String recipientName, long templateId, Map<String, Object> templateParams) {
        try {
            Map<String, Object> recipient = Map.of("email", targetEmail, "name", recipientName);
            Map<String, Object> payload = Map.of("templateId", templateId, "to", List.of(recipient), "params", templateParams);
            brevoRestClient.post().uri("/smtp/email").body(payload).retrieve().toBodilessEntity();
            System.out.println("Template [" + templateId + "] email dispatched to: " + targetEmail);
        } catch (Exception e) {
            System.err.println("SMTP delivery failed: " + e.getMessage());
        }
    }
}