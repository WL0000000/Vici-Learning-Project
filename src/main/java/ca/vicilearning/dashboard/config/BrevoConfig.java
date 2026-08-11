package ca.vicilearning.dashboard.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Configuration class responsible for setting up and exposing the Brevo API client.
 * Utilizes Spring's {@link RestClient} abstracting standard HTTP headers and base configurations.
 */
@Configuration
public class BrevoConfig {

    @Value("${brevo.api.url:https://api.brevo.com/v3}")
    private String apiUrl;

    @Value("${brevo.api.key}")
    private String apiKey;

    /**
     * Configures and initializes a thread-safe {@link RestClient} bean initialized 
     * with Brevo authentication credentials and mandatory content headers.
     *
     * @return Fully initialized RestClient instance.
     */
    @Bean
    public RestClient brevoRestClient() {
        // Brevo returns "category" / multiple-choice attributes (e.g. STUDENT_STATUS, and potentially
        // others) as JSON arrays even when they hold a single value. Several of our attribute fields
        // are typed as String, so a lone ["Active"] would make Jackson fail the ENTIRE /contacts
        // response and silently leave the roster at 0. UNWRAP_SINGLE_VALUE_ARRAYS lets a one-element
        // array bind to a scalar field, so one list-typed attribute can't sink the whole pull.
        ObjectMapper tolerantMapper = JsonMapper.builder()
                .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        return RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .messageConverters(converters ->
                        converters.add(0, new MappingJackson2HttpMessageConverter(tolerantMapper)))
                .build();
    }
}