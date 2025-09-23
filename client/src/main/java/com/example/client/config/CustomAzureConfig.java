package com.example.client.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class CustomAzureConfig {

    @Value("${azure.openai.embedding.endpoint}")
    private String embeddingEndpoint;

    @Value("${azure.openai.embedding.api-key}")
    private String embeddingApiKey;

    /**
     * Custom RestTemplate for embedding service
     */
    @Bean
    @Qualifier("embeddingRestTemplate")
    public RestTemplate embeddingRestTemplate() {
        RestTemplate template = new RestTemplate();

        // Add interceptor for authentication
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("api-key", embeddingApiKey);
            request.getHeaders().set("Content-Type", "application/json");
            return execution.execute(request, body);
        });

        return template;
    }

    @PostConstruct
    public void logConfiguration() {
        log.info("Custom Azure Configuration:");
        log.info("  Embedding Endpoint: {}", embeddingEndpoint);
        log.info("  Embedding API Key configured: {}",
                embeddingApiKey != null && !embeddingApiKey.isEmpty() ? "Yes" : "No");
    }
}
