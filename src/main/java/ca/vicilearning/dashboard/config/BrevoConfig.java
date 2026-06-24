package ca.vicilearning.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;


@Configuration
public class BrevoConfig {

    // Reads the URL from your application properties, defaulting to Brevo's v3 endpoint
    @Value("${brevo.api.url:https://api.brevo.com/v3}")
    private String apiUrl;

    // Reads your sandbox API key from your properties file
    @Value("${brevo.api.key}")
    private String apiKey;

    /**
     * Configures a globally available RestClient bean for Brevo communication.
     * This ensures all outbound calls automatically include the mandatory 
     * authentication headers required by Brevo's v3 REST API.
     */
    @Bean
    public RestClient brevoRestClient() {
        return RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}