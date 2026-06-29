package ca.vicilearning.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BrevoConfig {

    @Value("${brevo.api.url:https://api.brevo.com/v3}")
    private String apiUrl;

    @Value("${brevo.api.key}")
    private String apiKey;

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