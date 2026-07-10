package ca.vicilearning.dashboard.comms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating direct HTTP interactions with the Brevo marketing API platform.
 * Handles contact synchronizations, attribute state adjustments, and transactional SMTP dispatches.
 */
@Service
public class BrevoCommunicationService {

    private static final Logger log = LoggerFactory.getLogger(BrevoCommunicationService.class);

    // Endpoint URIs and API Defaults
    private static final String ENDPOINT_CONTACTS = "/contacts?limit=100&offset=0";
    private static final String ENDPOINT_CONTACT_BY_EMAIL = "/contacts/{email}";
    private static final String ENDPOINT_SMTP_EMAIL = "/smtp/email";
    private static final String DEFAULT_STATUS = "Active";

    // --- Inner DTO Node Data Enclaves (Records) ---

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
        // Brevo's top-level external id = the per-student EXT_ID (not an attribute).
        @JsonProperty("ext_id") String extId,
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
     * Pulls global contact configurations exactly once to generate an in-memory 
     * dictionary routing Account Keys to their explicit target parent email structures.
     *
     * @return Lookup map linking VICI Account IDs to Primary Emails.
     */
    public Map<String, String> fetchViciIdToEmailMap() {
        Map<String, String> lookupMap = new HashMap<>();
        try {
            BrevoListContactsResponse response = brevoRestClient.get()
                    .uri(ENDPOINT_CONTACTS) 
                    .retrieve()
                    .body(BrevoListContactsResponse.class);

            if (response != null && response.contacts() != null) {
                for (BrevoContactNode contact : response.contacts()) {
                    if (contact.attributes() != null && contact.attributes().viciAccountId() != null) {
                        String cleanViciId = contact.attributes().viciAccountId().trim().toUpperCase();
                        if (!cleanViciId.isEmpty() && contact.email() != null) {
                            lookupMap.put(cleanViciId, contact.email().trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed compiling VICI ID to Email mapping matrix.", e);
        }
        return lookupMap;
    }

    /**
     * Pulls contacts once and maps each contact's email (lower-cased) to its Brevo EXT_ID — the
     * per-student external id the sync stamps onto local students (matched by email). Returns an
     * empty map on any failure (e.g. no API key), so callers can treat that as "skip".
     *
     * @return Lookup map linking lower-cased contact email to EXT_ID.
     */
    public Map<String, String> fetchEmailToExtIdMap() {
        Map<String, String> lookupMap = new HashMap<>();
        try {
            BrevoListContactsResponse response = brevoRestClient.get()
                    .uri(ENDPOINT_CONTACTS)
                    .retrieve()
                    .body(BrevoListContactsResponse.class);

            if (response != null && response.contacts() != null) {
                for (BrevoContactNode contact : response.contacts()) {
                    if (contact.email() != null && contact.extId() != null && !contact.extId().isBlank()) {
                        lookupMap.put(contact.email().trim().toLowerCase(), contact.extId().trim());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed compiling email to EXT_ID mapping.", e);
        }
        return lookupMap;
    }

    /**
     * Pulls cumulative contact nodes and organizes them into a multi-tiered dictionary
     * structured as: Map<ViciAccountId, Map<LowercaseStudentName, ActivityStatus>>
     */
    public Map<String, Map<String, String>> fetchStudentStatusMap() {
        // Nested structure: Map<AccountId, Map<StudentName, Status>>
        Map<String, Map<String, String>> masterAccountMap = new HashMap<>();
        
        try {
            BrevoListContactsResponse response = brevoRestClient.get()
                    .uri(ENDPOINT_CONTACTS)
                    .retrieve()
                    .body(BrevoListContactsResponse.class);

            if (response != null && response.contacts() != null) {
                for (BrevoContactNode contact : response.contacts()) {
                    BrevoAttributesNode attributes = contact.attributes();
                    
                    // Enforce that we only process records with a valid family account identifier
                    if (attributes != null && attributes.viciAccountId() != null && !attributes.viciAccountId().isBlank()) {
                        String cleanAccountId = attributes.viciAccountId().trim().toUpperCase();
                        
                        // Isolate or initialize the nested collection bucket unique to this family row
                        Map<String, String> familyBucket = masterAccountMap.computeIfAbsent(cleanAccountId, k -> new HashMap<>());
                        
                        // Unpack the parallel strings directly into this family's protected bucket
                        unpackContactStatuses(attributes, familyBucket);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed executing nested account-scoped status mappings.", e);
        }
        return masterAccountMap;
    }

    /**
     * Extracted helper that populates a designated family mapping bucket with sibling statuses.
     */
    private void unpackContactStatuses(BrevoAttributesNode attributes, Map<String, String> familyBucket) {
        String namesRaw = attributes.studentNames();
        String statusesRaw = attributes.activityStatus();

        if (namesRaw == null || namesRaw.isBlank()) {
            return; 
        }

        String[] names = namesRaw.split(",");
        String[] statuses = (statusesRaw != null && !statusesRaw.isBlank()) 
                ? statusesRaw.split(",") 
                : new String[0];

        for (int i = 0; i < names.length; i++) {
            String cleanName = names[i].trim().toLowerCase();
            String cleanStatus = (i < statuses.length) ? statuses[i].trim() : DEFAULT_STATUS;
            
            if (!cleanName.isEmpty()) {
                familyBucket.put(cleanName, cleanStatus); 
            }
        }
    }

    /**
     * Updates profile attribute nodes remotely inside Brevo for a designated parent record.
     *
     * @param parentEmail      The target unique recipient lookup key.
     * @param attributePayload Key-value matrix representing CRM metadata keys to update.
     */
    public void updateContactAttributes(String parentEmail, Map<String, Object> attributePayload) {
        if (parentEmail == null || attributePayload == null) return;
        try {
            Map<String, Object> bodyWrapper = Map.of("attributes", attributePayload);
            brevoRestClient.put()
                    .uri(ENDPOINT_CONTACT_BY_EMAIL, parentEmail.trim())
                    .body(bodyWrapper)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Attributes successfully synchronized on Brevo container for: {}", parentEmail);
        } catch (Exception e) {
            log.error("Failed adjusting system configuration node for: {}", parentEmail, e);
        }
    }

    /**
     * Dispatches transactional templates out to specialized target accounts using SMTP parameters.
     *
     * @param targetEmail    Recipient communication address.
     * @param recipientName  Formatted descriptive recipient descriptor name.
     * @param templateId     Brevo managed pre-compiled graphic template ID number.
     * @param templateParams Injected runtime variables parsed into layout templates.
     */
    public void sendTemplatedEmail(String targetEmail, String recipientName, long templateId, Map<String, Object> templateParams) {
        try {
            Map<String, Object> recipient = Map.of("email", targetEmail, "name", recipientName);
            Map<String, Object> payload = Map.of(
                "templateId", templateId, 
                "to", List.of(recipient), 
                "params", templateParams
            );
            
            brevoRestClient.post()
                    .uri(ENDPOINT_SMTP_EMAIL)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Template ID [{}] successfully routed out to client: {}", templateId, targetEmail);
        } catch (Exception e) {
            log.error("SMTP delivery transaction aborted to target: {}", targetEmail, e);
        }
    }
}