package com.example.client.listener;

import com.example.client.controller.ChatController;
import com.example.client.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@AllArgsConstructor
public class WebSocketEventListener {
	private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
	@Autowired
	private SimpMessageSendingOperations messagingTemplate;

	@Autowired
	private ChatController chatController;

	@EventListener
	public void handleWebSocketConnectListener(SessionConnectedEvent event) {
		log.info("New WebSocket connection established");
	}

	@EventListener
	public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = headerAccessor.getSessionId();
		String username = (String) headerAccessor.getSessionAttributes().get("username");

		if (username != null) {
			log.info("User disconnected: {} (session: {})", username, sessionId);
			chatController.cleanupChatHistory(sessionId);

			ChatMessage chatMessage = new ChatMessage();
			chatMessage.setType(ChatMessage.MessageType.LEAVE);
			chatMessage.setSender(username);
			// Set timestamp for the LEAVE message
			chatMessage.setTimestamp(LocalDateTime.now().format(timeFormatter));
			messagingTemplate.convertAndSend("/topic/public", chatMessage);
		}
	}
}
