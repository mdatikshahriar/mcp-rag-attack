package com.example.client.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class McpToolService {
	private static final int MCP_CONNECTION_WAIT_MS = 3000;
	private static final int MAX_INITIALIZATION_ATTEMPTS = 3;
	private static final int RETRY_DELAY_MS = 2000;

	private final ChatClient chatClient;
	private final ToolCallbackProvider toolCallbackProvider;
	@Getter
	private volatile boolean initializationComplete = false;
	@Getter
	private volatile List<ToolDescription> availableTools = Collections.emptyList();

	public McpToolService(ChatClient.Builder chatClientBuilder, ToolCallbackProvider toolCallbackProvider) {
		this.chatClient = chatClientBuilder.build();
		this.toolCallbackProvider = toolCallbackProvider;
		log.info("McpToolService constructed - ChatClient: {}, ToolCallbackProvider: {}",
				this.chatClient != null ? "Initialized" : "NULL",
				this.toolCallbackProvider != null ? "Initialized" : "NULL");
	}

	public void initializeAndFetchTools() {
		log.info("Starting MCP Client initialization and tool discovery.");
		long initStartTime = System.currentTimeMillis();
		int attempt = 1;
		Exception lastException = null;

		while (attempt <= MAX_INITIALIZATION_ATTEMPTS && !initializationComplete) {
			try {
				log.info("MCP initialization attempt {} of {}", attempt, MAX_INITIALIZATION_ATTEMPTS);

				waitForMcpConnection(attempt);
				List<ToolDescription> tools = fetchAvailableTools(attempt);

				this.availableTools = tools != null ? Collections.unmodifiableList(tools) : Collections.emptyList();
				this.initializationComplete = true;
				logAvailableTools(this.availableTools);

				long totalInitTime = System.currentTimeMillis() - initStartTime;
				log.info(
						"MCP Client initialization completed successfully - Attempt: {}, Total time: {} ms, Tools found: {}",
						attempt, totalInitTime, this.availableTools.size());
				return;

			} catch (Exception e) {
				lastException = e;
				log.warn("MCP initialization attempt {} failed: {}", attempt, e.getMessage());

				if (attempt < MAX_INITIALIZATION_ATTEMPTS) {
					try {
						Thread.sleep((long) RETRY_DELAY_MS * attempt); // Exponential backoff
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						log.warn("MCP initialization interrupted during backoff.");
						break;
					}
				}
				attempt++;
			}
		}

		log.error("MCP Client initialization failed after {} attempts. Last error: {}", MAX_INITIALIZATION_ATTEMPTS,
				lastException != null ? lastException.getMessage() : "Unknown");
		this.availableTools = Collections.emptyList();
		// Still mark as complete to unblock application startup, which will operate in a degraded state.
		this.initializationComplete = true;
	}

	private void waitForMcpConnection(int attempt) throws InterruptedException {
		log.info("Waiting for MCP connection (simulated) - Attempt: {}", attempt);
		Thread.sleep(MCP_CONNECTION_WAIT_MS);
	}

	private List<ToolDescription> fetchAvailableTools(int attempt) {
		log.info("Fetching available MCP tools - Attempt: {}", attempt);
		try {
			String toolsPrompt = "List all available tools and their descriptions in JSON format. The format should be an array of objects, where each object has 'toolName' and 'toolDescription' fields.";
			List<ToolDescription> tools = chatClient.prompt(toolsPrompt)
					.toolCallbacks(toolCallbackProvider)
					.call()
					.entity(new ParameterizedTypeReference<>() {
					});

			log.info("Tools fetch completed - Tools found: {}", tools != null ? tools.size() : 0);
			return tools != null ? tools : Collections.emptyList();

		} catch (Exception e) {
			log.error("Error fetching MCP tools - Attempt: {}, Error: {}", attempt, e.getMessage(), e);
			throw new RuntimeException("Failed to fetch MCP tools", e);
		}
	}

	private void logAvailableTools(List<ToolDescription> tools) {
		if (tools == null || tools.isEmpty()) {
			log.warn("No MCP tools discovered or available.");
			return;
		}

		log.info("Available MCP Tools (Count: {}):", tools.size());
		StringBuilder toolsLog = new StringBuilder();
		for (int i = 0; i < tools.size(); i++) {
			ToolDescription tool = tools.get(i);
			if (tool != null) {
				toolsLog.append(String.format("\n  %d. Name: '%s', Description: '%s'", (i + 1), tool.toolName(),
						tool.toolDescription()));
			}
		}
		log.info(toolsLog.toString());
	}

	public record ToolDescription(String toolName,
			String toolDescription) {
	}
}
