package com.example.client.service;

import com.example.client.storage.InMemoryVectorStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UniversityRagService {
	private static final double SIMILARITY_THRESHOLD = 0.25;
	private static final int MAX_CONTEXT_RESULTS = 10;
	private static final int MAX_HISTORY_MESSAGES = 10;
	private static final int QUERY_TIMEOUT_SECONDS = 30;
	private static final int MAX_PROMPT_LENGTH = 50000;
	private static final int MAX_QUERY_LENGTH = 10000;

	private final InMemoryVectorStore vectorStore;
	private final ChatClient.Builder chatClientBuilder;
	private final ExecutorService executorService = Executors.newCachedThreadPool();

	public UniversityRagService(InMemoryVectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
		this.vectorStore = vectorStore;
		this.chatClientBuilder = chatClientBuilder;
	}

	public String processQuery(String userQuery, List<String> chatHistory) {
		if (!isValidQuery(userQuery)) {
			return "Please provide a valid question. Queries cannot be empty or excessively long.";
		}

		String sanitizedQuery = sanitizeQuery(userQuery);
		log.info("Processing RAG query: {}",
				sanitizedQuery.length() > 50 ? sanitizedQuery.substring(0, 50) + "..." : sanitizedQuery);

		try {
			return CompletableFuture.supplyAsync(() -> executeQueryWithRelationships(sanitizedQuery, chatHistory),
					executorService).get(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		} catch (TimeoutException e) {
			log.warn("Query timed out after {} seconds for query: {}", QUERY_TIMEOUT_SECONDS, sanitizedQuery);
			return generateTimeoutResponse(sanitizedQuery);
		} catch (Exception e) {
			log.error("Error processing RAG query: {}", sanitizedQuery, e);
			return generateErrorResponse(sanitizedQuery, e);
		}
	}

	private String executeQueryWithRelationships(String userQuery, List<String> chatHistory) {
		List<Map<String, Object>> initialResults = performVectorSearch(userQuery);
		List<Map<String, Object>> enhancedResults = performRelationshipAwareSearch(initialResults);
		ContextResult contextResult = buildContext(enhancedResults);
		List<String> cleanHistory = validateChatHistory(chatHistory);
		String prompt = buildPrompt(userQuery, contextResult, cleanHistory);
		return getLLMResponse(prompt, userQuery);
	}

	private boolean isValidQuery(String query) {
		return query != null && !query.trim().isEmpty() && query.length() <= MAX_QUERY_LENGTH
				&& !containsSuspiciousContent(query);
	}

	private boolean containsSuspiciousContent(String query) {
		String lowerQuery = query.toLowerCase();
		return lowerQuery.contains("delete from") || lowerQuery.contains("drop table") || lowerQuery.contains(
				"sql inject") || lowerQuery.contains("<script>");
	}

	private String sanitizeQuery(String query) {
		return query.trim()
				.replaceAll("[\\r\\n]+", " ")
				.replaceAll("\\s+", " ")
				.substring(0, Math.min(query.length(), MAX_QUERY_LENGTH));
	}

	private List<Map<String, Object>> performVectorSearch(String query) {
		try {
			List<Map<String, Object>> searchResults = vectorStore.hybridSearch(query, MAX_CONTEXT_RESULTS);
			log.debug("Hybrid search returned {} results for query: '{}'", searchResults.size(), query);
			return searchResults;
		} catch (Exception e) {
			log.warn("Hybrid search failed, falling back to simple vector search: {}", e.getMessage());
			try {
				return vectorStore.search(query, MAX_CONTEXT_RESULTS);
			} catch (Exception e2) {
				log.error("All search methods failed for query: '{}'", query, e2);
				return new ArrayList<>();
			}
		}
	}

	private List<Map<String, Object>> performRelationshipAwareSearch(List<Map<String, Object>> initialResults) {
		Set<String> relatedStudentIds = new HashSet<>();
		Set<String> relatedCourseIds = new HashSet<>();
		List<Map<String, Object>> enhancedResults = new ArrayList<>(initialResults);

		for (Map<String, Object> result : initialResults) {
			Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
			if (metadata == null)
				continue;

			String type = (String) metadata.get("type");
			if (type == null)
				continue;

			switch (type) {
				case "student" -> relatedStudentIds.add((String) metadata.get("id"));
				case "course" -> relatedCourseIds.add((String) metadata.get("id"));
				case "grade" -> {
					relatedStudentIds.add((String) metadata.get("studentId"));
					relatedCourseIds.add((String) metadata.get("courseId"));
				}
				case "research" -> {
					String authors = (String) metadata.get("authors");
					if (authors != null) {
						for (String author : authors.split(",")) {
							enhancedResults.addAll(vectorStore.search("author " + author.trim(), 5));
						}
					}
				}
			}
		}

		relatedStudentIds.remove(null);
		relatedCourseIds.remove(null);

		for (String studentId : relatedStudentIds) {
			enhancedResults.addAll(vectorStore.searchByMetadata("grade", "studentId", studentId, 10));
			enhancedResults.addAll(vectorStore.searchByMetadata("student", "id", studentId, 1));
		}
		for (String courseId : relatedCourseIds) {
			enhancedResults.addAll(vectorStore.searchByMetadata("grade", "courseId", courseId, 10));
			enhancedResults.addAll(vectorStore.searchByMetadata("course", "id", courseId, 1));
		}

		Map<String, Map<String, Object>> uniqueResults = new LinkedHashMap<>();
		for (Map<String, Object> result : enhancedResults) {
			String key = generateResultKey(result);
			uniqueResults.merge(key, result, (oldV, newV) -> {
				Double oldS = (Double) oldV.get("score");
				Double newS = (Double) newV.get("score");
				return (newS != null && (oldS == null || newS > oldS)) ? newV : oldV;
			});
		}

		List<Map<String, Object>> finalResults = new ArrayList<>(uniqueResults.values());
		finalResults.sort(
				(a, b) -> Double.compare((Double) b.getOrDefault("score", 0.0), (Double) a.getOrDefault("score", 0.0)));

		log.debug("Enhanced search: {} initial -> {} final unique results with relationships.", initialResults.size(),
				finalResults.size());
		return finalResults.stream().limit(MAX_CONTEXT_RESULTS * 2).collect(Collectors.toList());
	}

	private String generateResultKey(Map<String, Object> result) {
		Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
		if (metadata != null) {
			String type = (String) metadata.get("type");
			String id = (String) metadata.get("id");
			if ("grade".equals(type)) {
				return "grade:" + metadata.get("studentId") + ":" + metadata.get("courseId");
			}
			if (type != null && id != null) {
				return type + ":" + id;
			}
		}
		return "content:" + Objects.hash(result.get("content"));
	}

	private ContextResult buildContext(List<Map<String, Object>> searchResults) {
		if (searchResults.isEmpty()) {
			return new ContextResult("", false, 0, "No relevant information found.");
		}

		StringBuilder context = new StringBuilder("=== RELEVANT UNIVERSITY DATA ===\n\n");
		int validResults = 0;
		Set<String> resultTypes = new HashSet<>();

		for (Map<String, Object> result : searchResults) {
			Double score = (Double) result.get("score");
			if (score == null || score < SIMILARITY_THRESHOLD)
				continue;

			Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
			String content = (String) result.get("content");
			if (metadata == null && content == null)
				continue;

			String type = metadata != null ? (String) metadata.get("type") : "data";
			context.append(
					String.format("[Entry %d - %s - Relevance: %.3f]\n", validResults + 1, type.toUpperCase(), score));
			if (content != null && !content.trim().isEmpty()) {
				context.append("Content: ").append(content.trim()).append("\n");
			}
			if (metadata != null) {
				metadata.forEach((key, value) -> {
					if (value != null && !key.equals("chunkIndex") && !key.equals("totalChunks")) {
						context.append(String.format("  %s: %s\n", formatFieldName(key), value.toString()));
					}
				});
			}
			context.append("\n");
			validResults++;
			if (type != null)
				resultTypes.add(type);
		}

		String summary = String.format("Found %d relevant results of types: %s.", validResults,
				String.join(", ", resultTypes));
		context.append("=== SUMMARY ===\n").append(summary).append("\n");

		return new ContextResult(context.toString(), validResults > 0, validResults, summary);
	}

	private String formatFieldName(String fieldName) {
		String withSpaces = fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
		return withSpaces.substring(0, 1).toUpperCase() + withSpaces.substring(1);
	}

	private List<String> validateChatHistory(List<String> chatHistory) {
		if (chatHistory == null)
			return new ArrayList<>();
		return chatHistory.stream().filter(msg -> msg != null && !msg.trim().isEmpty() && msg.length() < 2000)
				.limit(MAX_HISTORY_MESSAGES)
				.collect(Collectors.toList());
	}

	private String buildPrompt(String userQuery, ContextResult contextResult, List<String> chatHistory) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("# SYSTEM ROLE\n")
				.append("You are a specialized University Assistant AI. Your answers must be based SOLELY on the retrieved data provided below. Do not invent information.\n")
				.append("Current Time: ")
				.append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
				.append("\n\n");

		if (contextResult.hasValidResults()) {
			prompt.append("# RETRIEVED UNIVERSITY DATA\n")
					.append("Base your answer on the following information. Higher relevance scores indicate better matches.\n\n")
					.append("```\n")
					.append(contextResult.context())
					.append("```\n\n");
		} else {
			prompt.append("# NO SPECIFIC DATA RETRIEVED\n")
					.append("No specific data matching the query was found. Acknowledge this and provide general guidance.\n\n");
		}

		if (!chatHistory.isEmpty()) {
			prompt.append("# CONVERSATION HISTORY (FOR CONTEXT)\n")
					.append(String.join("\n", chatHistory))
					.append("\n\n");
		}

		prompt.append("# CURRENT USER QUERY\n").append(userQuery).append("\n\n");

		prompt.append("# RESPONSE INSTRUCTIONS\n")
				.append("1. **Analyze the query and retrieved data.**\n")
				.append("2. **Synthesize a direct and accurate answer** based *only* on the provided data.\n")
				.append("3. **Format your response using Markdown.** Use lists, bolding (`**text**`), and code blocks (```) for clarity and readability.\n")
				.append("4. **If data is missing**, clearly state that the information is not available in the database.\n")
				.append("5. **Be concise and clear.** Structure your response for readability.\n")
				.append("6. **Do not apologize** for lack of information, just state facts.\n\n")
				.append("---\n**Begin Response:**\n");

		String finalPrompt = prompt.toString();
		if (finalPrompt.length() > MAX_PROMPT_LENGTH) {
			log.warn("Prompt length {} exceeds limit {}, truncating context.", finalPrompt.length(), MAX_PROMPT_LENGTH);
			int contextStart = finalPrompt.indexOf("```\n");
			int contextEnd = finalPrompt.lastIndexOf("```\n\n");
			if (contextStart != -1 && contextEnd > contextStart) {
				int availableSpace = MAX_PROMPT_LENGTH - (finalPrompt.length() - (contextEnd - contextStart));
				String truncatedContext = finalPrompt.substring(contextStart,
						Math.min(contextEnd, contextStart + availableSpace));
				finalPrompt =
						finalPrompt.substring(0, contextStart) + truncatedContext + "\n... [Context Truncated] ...\n"
								+ finalPrompt.substring(contextEnd);
			}
		}
		return finalPrompt;
	}

	private String getLLMResponse(String prompt, String originalQuery) {
		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				log.debug("LLM request attempt {} for query: '{}'", attempt, originalQuery);
				String response = chatClientBuilder.build().prompt(prompt).call().content();
				if (response != null && !response.trim().isEmpty()) {
					return response.trim();
				}
				log.warn("Received empty response from LLM on attempt {}", attempt);
			} catch (Exception e) {
				log.warn("LLM request failed on attempt {}: {}", attempt, e.getMessage());
				if (attempt == 3)
					throw e;
				try {
					Thread.sleep(1000 * attempt);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		return generateFallbackResponse(originalQuery);
	}

	private String generateFallbackResponse(String userQuery) {
		return "I am currently unable to process your request due to a technical issue. Please try again later.";
	}

	private String generateTimeoutResponse(String userQuery) {
		return "Your request is taking longer than expected to process. Please try simplifying your question or check back later.";
	}

	private String generateErrorResponse(String userQuery, Exception error) {
		return String.format(
				"I encountered an internal error (%s) while processing your request. Please rephrase your question or contact support if the issue persists.",
				error.getClass().getSimpleName());
	}

	@PreDestroy
	public void shutdown() {
		log.info("Shutting down University RAG Service executor...");
		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
				executorService.shutdownNow();
			}
		} catch (InterruptedException e) {
			executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
		log.info("Executor shutdown complete.");
	}

	private record ContextResult(String context,
			boolean hasValidResults,
			int resultCount,
			String summary) {
	}
}
