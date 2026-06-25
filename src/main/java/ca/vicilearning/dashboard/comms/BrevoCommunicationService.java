package ca.vicilearning.dashboard.comms;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

// Data Transfer Objects (DTOs) tailored for Brevo's JSON structure
record Recipient(String email, String name) {}

record BrevoEmailPayload(
    Long templateId,
    List<Recipient> to,
    Map<String, Object> params
) {}

@Service
public class BrevoCommunicationService {

    private final RestClient brevoRestClient;

    // Spring automatically injects the RestClient bean we configured in Step 2 earlier
    public BrevoCommunicationService(RestClient brevoRestClient) {
        this.brevoRestClient = brevoRestClient;
    }

    /**
     * Sends a templated email using Brevo's v3 SMTP endpoint.
     */
    public boolean sendTemplatedEmail(String toEmail, String recipientName, Long templateId, Map<String, Object> params) {
        try {
            // 1. Pack the data into the structure Brevo expects
            BrevoEmailPayload payload = new BrevoEmailPayload(
                templateId,
                List.of(new Recipient(toEmail, recipientName)),
                params
            );

            // 2. Make the asynchronous/synchronous POST call to /smtp/email
            brevoRestClient.post()
                    .uri("/smtp/email")
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            System.err.println("CRITICAL: Brevo 300 free daily email limit exhausted (429 Rate Limit)!");
                        } else {
                            System.err.println("Brevo Client Error occurred. Status: " + response.getStatusCode());
                        }
                    })
                    .toBodilessEntity(); // We just need to know if it succeeded (201 Created)

            return true;
        } catch (Exception e) {
            System.err.println("Network exception while communicating with Brevo: " + e.getMessage());
            return false;
        }
    }
}