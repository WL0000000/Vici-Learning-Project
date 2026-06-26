package ca.vicilearning.dashboard.comms;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Objects (DTOs) strictly modeling Brevo's outbound JSON payloads.
 */
record Recipient(String email, String name) {}

record BrevoEmailPayload(
    Long templateId,
    List<Recipient> to,
    Map<String, Object> params
) {}

@Service
public class BrevoCommunicationService {

    private final RestClient brevoRestClient;

    public BrevoCommunicationService(RestClient brevoRestClient) {
        this.brevoRestClient = brevoRestClient;
    }

    /**
     * Syncs custom flat contact attributes to a Parent profile row inside Brevo CRM[cite: 251].
     * Pushes properties like VICI_ACCOUNT_ID, STUDENT_NAMES, and PAYMENT_STATUS[cite: 304].
     * * @param email The target unique identifier for the parent account[cite: 23, 239, 303].
     * @param attributes Map containing custom field key-value allocations.
     */
    public void updateContactAttributes(String email, Map<String, Object> attributes) {
        try {
            brevoRestClient.put()
                .uri("/contacts/{email}", email)
                .body(Map.of("attributes", attributes))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    System.err.println("CRM Attribute Sync Client Error: " + response.getStatusCode());
                })
                .toBodilessEntity();
            System.out.println("CRM Attributes successfully pushed for parent target: " + email);
        } catch (Exception e) {
            System.err.println("Network exception updating Brevo contact properties: " + e.getMessage());
        }
    }

    /**
     * Signals Brevo's SMTP server engine to issue a personalized transactional email template[cite: 20, 251].
     */
    public boolean sendTemplatedEmail(String toEmail, String recipientName, Long templateId, Map<String, Object> params) {
        try {
            BrevoEmailPayload payload = new BrevoEmailPayload(
                templateId,
                List.of(new Recipient(toEmail, recipientName)),
                params
            );

            brevoRestClient.post()
                    .uri("/smtp/email")
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            System.err.println("CRITICAL: Brevo 300 free daily email limit exhausted (429 Rate Limit)!");
                        } else {
                            System.err.println("Brevo SMTP Transfer Client Error: " + response.getStatusCode());
                        }
                    })
                    .toBodilessEntity();

            return true;
        } catch (Exception e) {
            System.err.println("Network exception during outbound Brevo transaction: " + e.getMessage());
            return false;
        }
    }
}