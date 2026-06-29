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
     * Hits the global GET /contacts endpoint exactly ONCE.
     * Compiles an in-memory dictionary of VICI_ACCOUNT_ID -> primary email string.
     */
    public Map<String, String> fetchViciIdToEmailMap() {
        Map<String, String> lookupMap = new HashMap<>();
        try {
            BrevoListContactsResponse response = brevoRestClient.get()
                    .uri("/contacts?limit=100&offset=0") 
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
        } catch (Exception e) {
            System.err.println("[SERVICE ERROR] Failed compiling VICI ID to Email map: " + e.getMessage());
        }
        return lookupMap;
    }

    /**
     * Compiles a fast map of individual student names (lowercase) to their live status inside Brevo.
     * This dynamically unpacks the comma-separated parallel string tokens safely.
     */
    public Map<String, String> fetchStudentStatusMap() {
        Map<String, String> statusMap = new HashMap<>();
        try {
            BrevoListContactsResponse response = brevoRestClient.get()
                    .uri("/contacts?limit=100&offset=0")
                    .retrieve()
                    .body(BrevoListContactsResponse.class);

            if (response != null && response.contacts() != null) {
                for (BrevoContactNode contact : response.contacts()) {
                    if (contact.attributes() != null) {
                        String namesRaw = contact.attributes().studentNames();
                        String statusesRaw = contact.attributes().activityStatus();

                        if (namesRaw != null && !namesRaw.isBlank()) {
                            String[] names = namesRaw.split(",");
                            String[] statuses = (statusesRaw != null && !statusesRaw.isBlank()) 
                                    ? statusesRaw.split(",") 
                                    : new String[0];

                            for (int i = 0; i < names.length; i++) {
                                String cleanName = names[i].trim().toLowerCase();
                                String cleanStatus = (i < statuses.length) ? statuses[i].trim() : "Active";
                                if (!cleanName.isEmpty()) {
                                    statusMap.put(cleanName, cleanStatus);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVICE ERROR] Failed compiling student status map: " + e.getMessage());
            e.printStackTrace();
        }
        return statusMap;
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