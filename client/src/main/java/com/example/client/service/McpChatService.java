package com.example.client.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class McpChatService {
    private final UniversityRagService ragService;
    private final ChatClient.Builder chatClientBuilder;
    private final MessageFilterService messageFilterService;

    public String processQuery(String userQuery, List<String> chatHistory) {
        try {
            log.info("Processing query: {}",
                    userQuery.length() > 50 ? userQuery.substring(0, 50) + "..." : userQuery);

            MessageFilterService.FilterResult filterResult =
                    messageFilterService.filterMessage(userQuery);

            if ("CONVERSATIONAL".equals(filterResult.toolType())) {
                log.info("Handling simple conversational query directly with LLM.");
                return processSimpleConversation(filterResult.processedMessage(), chatHistory);
            } else {
                log.info("Handling complex query with RAG-first and tool-use approach.");
                // The RAG service now handles the potential for tool use internally.
                return ragService.processQuery(userQuery, chatHistory);
            }

        } catch (Exception e) {
            log.error("Error processing query: {}", e.getMessage(), e);
            return "I apologize, but I encountered an error processing your request. Please try again or contact support.";
        }
    }

    private String processSimpleConversation(String processedMessage, List<String> chatHistory) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append(
                    "You are a friendly and helpful University Assistant. Respond to the user's simple conversational message concisely.\n\n");

            if (chatHistory != null && !chatHistory.isEmpty()) {
                // Provide a concise history
                String history = chatHistory.stream().limit(5).collect(Collectors.joining("\n"));
                prompt.append("Previous conversation for context:\n").append(history)
                        .append("\n\n");
            }

            prompt.append("Current user message: ").append(processedMessage);

            // Build a chat client WITHOUT tool callbacks for simple conversations
            String response = chatClientBuilder.build().prompt(prompt.toString()).call().content();

            return response != null ? response : "I'm not sure how to respond to that.";

        } catch (Exception e) {
            log.error("Error processing simple conversation: {}", e.getMessage());
            // Fallback to the RAG service if the simple path fails, just in case.
            return ragService.processQuery(processedMessage, chatHistory);
        }
    }
}
