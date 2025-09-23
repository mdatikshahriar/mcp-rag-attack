package com.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
	private MessageType type;
	private String content;
	private String sender;
	private String timestamp;

	public enum MessageType {
		CHAT,
		JOIN,
		LEAVE
	}
}
