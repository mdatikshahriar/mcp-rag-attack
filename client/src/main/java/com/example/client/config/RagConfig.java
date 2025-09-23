package com.example.client.config;

import com.example.client.service.AzureEmbeddingService;
import com.example.client.storage.InMemoryVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public InMemoryVectorStore vectorStore(
            @Autowired(required = false) EmbeddingModel embeddingModel,
            AzureEmbeddingService azureEmbeddingService) {
        return new InMemoryVectorStore(embeddingModel, azureEmbeddingService);
    }
}
