package ca.vicilearning.dashboard.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simplybook")
public record SimplybookProperties(
        String companyLogin,
        String apiKey,
        String adminUsername,
        String adminPassword,
        String loginUrl,
        String adminUrl,
        long syncIntervalMs,
        // REST API v2 (user-api-v2.simplybook.me) — needed for custom client fields
        // (Account_ID), memberships, and invoices, which JSON-RPC cannot return.
        String apiV2Url,
        // Optional API User Key. Preferred over adminPassword for REST v2 auth because
        // it bypasses IP verification (important for cloud hosts with rotating IPs).
        String apiUserKey,
        // Title of the SimplyBook custom client field that holds the Brevo account link.
        String accountIdFieldTitle
) {
    // The secret sent to REST v2 /admin/auth: an API User Key if configured, else the
    // admin password. Centralised so the auth path has one source of truth.
    public String restAuthSecret() {
        return (apiUserKey != null && !apiUserKey.isBlank()) ? apiUserKey : adminPassword;
    }

    // True only when we have enough config to attempt a REST v2 call. Lets the sync skip
    // the Account_ID step cleanly (rather than failing) when REST creds aren't set yet.
    public boolean restConfigured() {
        return companyLogin != null && !companyLogin.isBlank()
                && restAuthSecret() != null && !restAuthSecret().isBlank();
    }
}
