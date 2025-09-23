package com.example.client.controller;

import com.example.client.model.ChatMessage;
import com.example.client.service.McpChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
public class ChatController {
	private static final int MAX_CHAT_HISTORY_SIZE = 10;
	private final ConcurrentHashMap<String, List<String>> chatHistoryMap = new ConcurrentHashMap<>();
	private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

	@Autowired
	private SimpMessagingTemplate messagingTemplate;
	@Autowired
	private McpChatService mcpChatService;
	@Autowired
	@Qualifier("asyncTaskExecutor")
	private TaskExecutor taskExecutor;

	@MessageMapping("/chat.sendMessage")
	@SendTo("/topic/public")
	public ChatMessage sendMessage(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
		String sessionId = headerAccessor.getSessionId();
		// Set timestamp for the user's message
		chatMessage.setTimestamp(LocalDateTime.now().format(timeFormatter));

		// Process query asynchronously using a managed thread pool
		taskExecutor.execute(() -> {
			try {
				String userQuery = chatMessage.getContent().trim();
				List<String> chatHistory = chatHistoryMap.getOrDefault(sessionId, new ArrayList<>());

				// Add user message to history
				chatHistory.add("User: " + userQuery);

				// Get AI response using the integrated service
				String aiResponse = mcpChatService.processQuery(userQuery, chatHistory);

				// Add AI response to history
				chatHistory.add("Assistant: " + aiResponse);

				// Manage history size
				if (chatHistory.size() > MAX_CHAT_HISTORY_SIZE) {
					chatHistory = new ArrayList<>(
							chatHistory.subList(chatHistory.size() - MAX_CHAT_HISTORY_SIZE, chatHistory.size()));
				}
				chatHistoryMap.put(sessionId, chatHistory);

				// Send AI response
				ChatMessage aiMessage = new ChatMessage();
				aiMessage.setType(ChatMessage.MessageType.CHAT);
				aiMessage.setSender("University Assistant");
				aiMessage.setContent(aiResponse);
				// Set timestamp for the AI's message
				aiMessage.setTimestamp(LocalDateTime.now().format(timeFormatter));

				messagingTemplate.convertAndSend("/topic/public", aiMessage);

			} catch (Exception e) {
				log.error("Error processing message: {}", e.getMessage(), e);

				ChatMessage errorMessage = new ChatMessage();
				errorMessage.setType(ChatMessage.MessageType.CHAT);
				errorMessage.setSender("University Assistant");
				errorMessage.setContent("I apologize, but I encountered an error. Please try again.");
				// Set timestamp for the error message
				errorMessage.setTimestamp(LocalDateTime.now().format(timeFormatter));

				messagingTemplate.convertAndSend("/topic/public", errorMessage);
			}
		});

		return chatMessage;
	}

	@MessageMapping("/chat.addUser")
	@SendTo("/topic/public")
	public ChatMessage addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
		String sessionId = headerAccessor.getSessionId();
		headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
		chatHistoryMap.put(sessionId, new ArrayList<>());
		// Set timestamp for the JOIN message
		chatMessage.setTimestamp(LocalDateTime.now().format(timeFormatter));
		return chatMessage;
	}

	public void cleanupChatHistory(String sessionId) {
		chatHistoryMap.remove(sessionId);
		log.info("Cleaned up chat history for session: {}", sessionId);
	}
}
