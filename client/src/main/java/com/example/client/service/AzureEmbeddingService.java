package com.example.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AzureEmbeddingService {
	private static final int MAX_TEXT_LENGTH = 8192;
	private static final int EMBEDDING_DIMENSION = 3072;
	private static final int MAX_RETRY_ATTEMPTS = 3;
	private static final long RETRY_DELAY_MS = 1000;

	private final RestTemplate restTemplate;
	private final String endpoint;
	private final ObjectMapper objectMapper;
	private final HttpHeaders headers;

	// Metrics
	private final AtomicLong totalEmbeddingsGenerated = new AtomicLong(0);
	private final AtomicLong failedOperations = new AtomicLong(0);

	public AzureEmbeddingService(@Qualifier("embeddingRestTemplate") RestTemplate restTemplate,
			@Value("${azure.openai.embedding.endpoint}") String endpoint) {
		this.restTemplate = restTemplate;
		this.endpoint = endpoint;
		this.objectMapper = new ObjectMapper();

		this.headers = new HttpHeaders();
		this.headers.setContentType(MediaType.APPLICATION_JSON);

		log.info("Azure Embedding Service initialized - Endpoint: {}", endpoint);
	}

	public float[] generateEmbedding(String text) {
		if (!isValidInput(text)) {
			log.warn("Invalid input provided for embedding generation");
			failedOperations.incrementAndGet();
			return createZeroVector();
		}

		String sanitizedText = sanitizeInput(text);

		for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
			try {
				float[] embedding = callAzureEmbeddingAPI(sanitizedText);
				if (embedding != null && embedding.length == EMBEDDING_DIMENSION) {
					totalEmbeddingsGenerated.incrementAndGet();
					return embedding;
				}

				log.warn("Invalid embedding received from Azure API, attempt {}", attempt);

			} catch (Exception e) {
				log.warn("Azure embedding API call failed, attempt {}: {}", attempt, e.getMessage());

				if (attempt == MAX_RETRY_ATTEMPTS) {
					failedOperations.incrementAndGet();
					log.error("All embedding attempts failed for text: {}",
							sanitizedText.substring(0, Math.min(50, sanitizedText.length())));
					return createZeroVector();
				}

				try {
					Thread.sleep(RETRY_DELAY_MS * attempt);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					log.warn("Retry delay interrupted. Aborting embedding generation.");
					break;
				}
			}
		}

		failedOperations.incrementAndGet();
		return createZeroVector();
	}

	private float[] callAzureEmbeddingAPI(String text) {
		try {
			log.debug("Making embedding API call to: {}", endpoint);
			log.debug("Text length: {}", text.length());

			Map<String, Object> requestBody = Map.of("input", text, "encoding_format", "float");
			String jsonRequest = objectMapper.writeValueAsString(requestBody);

			log.debug("Request body: {}", jsonRequest);

			HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);
			ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);

			log.debug("Response status: {}", response.getStatusCode());
			// Limit response preview size
			if (response.getBody() != null) {
				String preview = response.getBody().length() > 100 ?
						response.getBody().substring(0, 100) + "..." :
						response.getBody();
				log.debug("Response body preview: {}", preview);
			}

			if (response.getStatusCode().is2xxSuccessful()) {
				return parseEmbeddingResponse(response.getBody());
			} else {
				log.error("Azure API returned error status: {}", response.getStatusCode());
				return null;
			}

		} catch (Exception e) {
			log.error("Error calling Azure embedding API", e);
			throw new RuntimeException("Azure embedding API call failed", e);
		}
	}

	private float[] parseEmbeddingResponse(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode data = root.get("data");

			if (data != null && data.isArray() && !data.isEmpty()) {
				JsonNode embeddingNode = data.get(0).get("embedding");

				if (embeddingNode != null && embeddingNode.isArray()) {
					float[] result = new float[embeddingNode.size()];
					for (int i = 0; i < embeddingNode.size(); i++) {
						result[i] = (float) embeddingNode.get(i).asDouble();
					}
					return result;
				}
			}

			log.error("Invalid response structure from Azure API: {}", responseBody);
			return null;

		} catch (Exception e) {
			log.error("Error parsing Azure embedding response", e);
			return null;
		}
	}

	private float[] createZeroVector() {
		return new float[EMBEDDING_DIMENSION];
	}

	private boolean isValidInput(String text) {
		return text != null && !text.trim().isEmpty() && !containsMaliciousContent(text);
	}

	private boolean containsMaliciousContent(String text) {
		String lowerText = text.toLowerCase();
		return lowerText.contains("<script") || lowerText.contains("javascript:") || text.contains("\0")
				|| text.contains("\uFEFF");
	}

	private String sanitizeInput(String text) {
		String processedText = text.trim().replaceAll("[\r\n\t]+", " ").replaceAll("\\s+", " ");
		// Truncate the processed text to the max length.
		return processedText.substring(0, Math.min(processedText.length(), MAX_TEXT_LENGTH));
	}
}
