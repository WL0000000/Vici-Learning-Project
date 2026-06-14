package ca.vicilearning.dashboard.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simplybook")
public record SimplybookProperties(
        String companyLogin,
        String apiKey,
        String adminUsername,
        String adminPassword
) {}
