package ca.vicilearning.dashboard.notion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notion")
public record NotionProperties(
        String token,
        String tutorsDataSourceId,
        String tutorsDatabaseId,
        String apiBaseUrl
) {}
