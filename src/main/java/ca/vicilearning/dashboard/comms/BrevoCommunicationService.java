package ca.vicilearning.dashboard.comms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
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
    private static final String ENDPOINT_CONTACT_BY_EMAIL = "/contacts/{email}";
    private static final String ENDPOINT_SMTP_EMAIL = "/smtp/email";
    private static final String DEFAULT_STATUS = "Active";

    // Company page size for the family-link pull (Brevo allows up to 100/page). The contact page
    // size is injectable (see constructor, default 1000) so tests can force a multi-page path.
    private static final int COMPANIES_PAGE_SIZE = 100;

    // --- Inner DTO Node Data Enclaves (Records) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrevoAttributesNode(
        @JsonProperty("VICI_ACCOUNT_ID") String viciAccountId,
        @JsonProperty("STUDENT_NAMES") String studentNames,
        // Enrolment status (ACTIVE/PAUSED) — distinct from ACTIVITY_STATUS, which drives lapse
        // detection. Read per contact for the StudentStatus sync.
        @JsonProperty("STUDENT_STATUS") String studentStatus,
        @JsonProperty("ACTIVITY_STATUS") String activityStatus,
        @JsonProperty("LAST_BOOKING_DATE") String lastBookingDate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrevoContactNode(
        // Brevo's numeric contact id. This is what a Company's linkedContactsIds reference, so it's
        // the join key between a family Company and its student contacts.
        @JsonProperty("id") Long id,
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrevoCompanyNode(
        @JsonProperty("id") String id,
        // The family name lives in attributes.name (free text); there is no dedicated Account_ID
        // field on a Brevo company (confirmed via GET /companies/attributes).
        @JsonProperty("attributes") Map<String, Object> attributes,
        // Numeric ids of the contacts (students) linked to this company/family.
        @JsonProperty("linkedContactsIds") List<Long> linkedContactsIds
    ) {
        public String name() {
            Object n = (attributes == null) ? null : attributes.get("name");
            return n == null ? null : n.toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrevoListCompaniesResponse(
        @JsonProperty("items") List<BrevoCompanyNode> items
    ) {}

    /**
     * A Brevo family Company reduced to what the family-link sync needs: the free-text family
     * {@code name} and the numeric ids of its linked student contacts.
     */
    public record CompanyLink(String name, List<Long> contactIds) {}

    private final RestClient brevoRestClient;
    private final int contactsPageSize;

    public BrevoCommunicationService(RestClient brevoRestClient,
            @Value("${brevo.contacts-page-size:1000}") int contactsPageSize) {
        this.brevoRestClient = brevoRestClient;
        this.contactsPageSize = contactsPageSize;
    }

    /**
     * Pulls <b>every</b> Brevo contact, paging past the API's per-request cap (default 1000/page) so
     * callers never silently miss records once Vici exceeds one page. Returns whatever was read
     * (empty on an immediate failure — e.g. no API key), so callers can treat that as "skip".
     */
    private List<BrevoContactNode> fetchAllContacts() {
        List<BrevoContactNode> all = new ArrayList<>();
        try {
            int offset = 0;
            while (true) {
                BrevoListContactsResponse response = brevoRestClient.get()
                        .uri("/contacts?limit={limit}&offset={offset}", contactsPageSize, offset)
                        .retrieve()
                        .body(BrevoListContactsResponse.class);

                if (response == null || response.contacts() == null || response.contacts().isEmpty()) {
                    break;
                }
                all.addAll(response.contacts());
                if (response.contacts().size() < contactsPageSize) {
                    break;
                }
                offset += contactsPageSize;
            }
        } catch (Exception e) {
            log.error("Failed fetching Brevo contacts (paginated).", e);
        }
        return all;
    }

    /**
     * Pulls global contact configurations exactly once to generate an in-memory 
     * dictionary routing Account Keys to their explicit target parent email structures.
     *
     * @return Lookup map linking VICI Account IDs to Primary Emails.
     */
    public Map<String, String> fetchViciIdToEmailMap() {
        Map<String, String> lookupMap = new HashMap<>();
        for (BrevoContactNode contact : fetchAllContacts()) {
            if (contact.attributes() != null && contact.attributes().viciAccountId() != null) {
                String cleanViciId = contact.attributes().viciAccountId().trim().toUpperCase();
                if (!cleanViciId.isEmpty() && contact.email() != null) {
                    lookupMap.put(cleanViciId, contact.email().trim());
                }
            }
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
        for (BrevoContactNode contact : fetchAllContacts()) {
            if (contact.email() != null && contact.extId() != null && !contact.extId().isBlank()) {
                lookupMap.put(contact.email().trim().toLowerCase(), contact.extId().trim());
            }
        }
        return lookupMap;
    }

    /**
     * Maps each contact's email (lower-cased) to its raw Brevo {@code STUDENT_STATUS} string — the
     * enrolment status the {@link ca.vicilearning.dashboard.sync.SyncService} parses to
     * {@code ACTIVE}/{@code PAUSED} and stamps onto local students (matched by email). Contacts with
     * no status are omitted, so a sync only overrides a local status when Brevo specifies one. Empty
     * on any failure (e.g. no API key), so the caller skips cleanly.
     *
     * <p><b>Assumption to verify against real data (Meeting #4 field-shape check):</b> this reads
     * {@code STUDENT_STATUS} as a <em>per-contact</em> attribute matched by email, consistent with the
     * one-student = one-Brevo-contact identity model. If Vici's Brevo instead stores it as a parallel
     * list on a family/parent contact (like {@code STUDENT_NAMES}/{@code ACTIVITY_STATUS}), the match
     * key would move to account-id + name — swap the mapping here.
     */
    public Map<String, String> fetchEmailToStatusMap() {
        Map<String, String> lookupMap = new HashMap<>();
        for (BrevoContactNode contact : fetchAllContacts()) {
            if (contact.email() == null || contact.attributes() == null) {
                continue;
            }
            String status = contact.attributes().studentStatus();
            if (status != null && !status.isBlank()) {
                lookupMap.put(contact.email().trim().toLowerCase(), status.trim());
            }
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
        for (BrevoContactNode contact : fetchAllContacts()) {
            BrevoAttributesNode attributes = contact.attributes();
            // Only process records with a valid family account identifier.
            if (attributes != null && attributes.viciAccountId() != null && !attributes.viciAccountId().isBlank()) {
                String cleanAccountId = attributes.viciAccountId().trim().toUpperCase();
                Map<String, String> familyBucket = masterAccountMap.computeIfAbsent(cleanAccountId, k -> new HashMap<>());
                unpackContactStatuses(attributes, familyBucket);
            }
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
     * Pulls every family Company from Brevo (paginated), each reduced to its free-text name and the
     * numeric ids of its linked student contacts. This is the association link that Brevo's contact
     * CSV export omits but the API exposes. Returns an empty list on any failure (no key, CRM feature
     * off, or no companies), so callers treat that as "nothing to link" and skip cleanly.
     */
    public List<CompanyLink> fetchCompanies() {
        List<CompanyLink> companies = new ArrayList<>();
        try {
            int page = 1;
            while (true) {
                BrevoListCompaniesResponse response = brevoRestClient.get()
                        .uri("/companies?limit={limit}&page={page}", COMPANIES_PAGE_SIZE, page)
                        .retrieve()
                        .body(BrevoListCompaniesResponse.class);

                if (response == null || response.items() == null || response.items().isEmpty()) {
                    break;
                }
                for (BrevoCompanyNode node : response.items()) {
                    companies.add(new CompanyLink(
                            node.name(),
                            node.linkedContactsIds() == null ? List.of() : node.linkedContactsIds()));
                }
                if (response.items().size() < COMPANIES_PAGE_SIZE) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Failed fetching Brevo companies for family-link sync.", e);
        }
        return companies;
    }

    /**
     * Pulls all contacts (paginated past the 100 cap the other methods hit) and maps each Brevo
     * numeric contact id to its email. Lets the family-link sync resolve a Company's
     * {@code linkedContactsIds} to local students, which are matched to Brevo by email. Returns an
     * empty map on any failure.
     */
    public Map<Long, String> fetchContactIdToEmailMap() {
        Map<Long, String> lookupMap = new HashMap<>();
        for (BrevoContactNode contact : fetchAllContacts()) {
            if (contact.id() != null && contact.email() != null && !contact.email().isBlank()) {
                lookupMap.put(contact.id(), contact.email().trim());
            }
        }
        return lookupMap;
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