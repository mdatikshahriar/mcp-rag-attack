package com.example.client.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class McpChatService {
	private final UniversityRagService ragService;
	private final ChatClient.Builder chatClientBuilder;
	private final ToolCallbackProvider toolCallbackProvider;
	private final MessageFilterService messageFilterService;

	public String processQuery(String userQuery, List<String> chatHistory) {
		try {
			log.info("Processing query: {}", userQuery.length() > 50 ? userQuery.substring(0, 50) + "..." : userQuery);

			MessageFilterService.FilterResult filterResult = messageFilterService.filterMessage(userQuery);

			if (filterResult.canBeHandledByMcp()) {
				log.info("Using MCP tools for: {}", filterResult.toolType());
				return processWithMcpTools(filterResult.processedMessage(), chatHistory);
			} else {
				log.info("Using RAG for regular query");
				return ragService.processQuery(userQuery, chatHistory);
			}

		} catch (Exception e) {
			log.error("Error processing query: {}", e.getMessage(), e);
			return "I apologize, but I encountered an error processing your request. Please try again or contact support.";
		}
	}

	private String processWithMcpTools(String processedMessage, List<String> chatHistory) {
		try {
			StringBuilder prompt = new StringBuilder();
			prompt.append(
					"You are a helpful assistant. Use the available tools to answer the user's request. Format your final response to the user in Markdown for better readability.\n\n");

			if (chatHistory != null && !chatHistory.isEmpty()) {
				// Provide a concise history
				String history = chatHistory.stream().limit(5).collect(Collectors.joining("\n"));
				prompt.append("Previous conversation for context:\n").append(history).append("\n\n");
			}

			prompt.append("Current user request: ").append(processedMessage);

			String response = chatClientBuilder.build().prompt(prompt.toString())
					.toolCallbacks(toolCallbackProvider)
					.call()
					.content();

			return response != null ?
					response :
					"I couldn't generate a response using the available tools. Could you please rephrase?";

		} catch (Exception e) {
			log.error("Error with MCP tools: {}. Falling back to RAG system.", e.getMessage());
			return ragService.processQuery("Please help with: " + processedMessage, chatHistory);
		}
	}
}
