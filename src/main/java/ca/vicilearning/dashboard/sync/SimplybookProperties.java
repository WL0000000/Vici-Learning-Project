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
        // Optional API User Key. Preferred over adminPassword for BOTH the JSON-RPC and REST v2
        // logins because it bypasses SimplyBook's trusted-IP restriction and 2FA — password auth
        // is blocked from a new IP (e.g. Render) and prompts 2FA, so the key is the only thing that
        // authenticates from a cloud host.
        String apiUserKey,
        // Title of the SimplyBook custom client field that holds the Brevo account link.
        String accountIdFieldTitle
) {
    // The secret sent to SimplyBook auth — both the JSON-RPC getUserToken login and the REST v2
    // /admin/auth: an API User Key if configured, else the admin password. Centralised so both
    // clients share one source of truth. Prefer the key: it's the only credential that works from
    // a cloud host, since password auth is IP-restricted and 2FA-gated.
    public String authSecret() {
        return (apiUserKey != null && !apiUserKey.isBlank()) ? apiUserKey : adminPassword;
    }

    // True only when we have enough config to attempt a REST v2 call. Lets the sync skip
    // the Account_ID step cleanly (rather than failing) when REST creds aren't set yet.
    public boolean restConfigured() {
        return companyLogin != null && !companyLogin.isBlank()
                && authSecret() != null && !authSecret().isBlank();
    }
}
