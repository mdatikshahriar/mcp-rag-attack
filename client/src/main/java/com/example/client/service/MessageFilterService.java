package com.example.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MessageFilterService {
    // Patterns for simple, stateless conversational turns that don't require RAG or tools.
    private static final List<Pattern> CONVERSATIONAL_PATTERNS = List.of(Pattern.compile(
                    "(?i)^\\s*(hi|hello|hey|greetings|good morning|good afternoon|good evening)\\s*[.!?]?$"),
            Pattern.compile("(?i)^\\s*(bye|goodbye|see you|later|cya)\\s*[.!]?$"),
            Pattern.compile("(?i)^\\s*(thanks|thank you|thx|ty|appreciate it)\\s*[.!]?$"),
            Pattern.compile("(?i)^\\s*(how are you|how's it going|what's up)\\s*\\??$"),
            Pattern.compile("(?i)^\\s*(ok|okay|got it|understood)\\s*[.!]?$"));

    public FilterResult filterMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new FilterResult(false, "Empty message", "COMPLEX", message);
        }

        String trimmedMessage = message.trim();
        log.info("Filtering message: {}", trimmedMessage.length() > 50 ?
                trimmedMessage.substring(0, 50) + "..." :
                trimmedMessage);

        for (Pattern pattern : CONVERSATIONAL_PATTERNS) {
            if (pattern.matcher(trimmedMessage).matches()) {
                log.info("Simple conversational query detected.");
                // Flag this for a special, non-RAG path.
                return new FilterResult(true, "Simple conversation", "CONVERSATIONAL",
                        trimmedMessage);
            }
        }

        log.info("Complex query detected. Routing to RAG-first process.");
        // This is the default path for all non-conversational queries.
        return new FilterResult(false, "Complex query for RAG", "COMPLEX", trimmedMessage);
    }

    /**
     * @param canBeHandledByMcp A flag indicating if a special, non-RAG path should be taken (true
     *                          for conversational).
     * @param reason            A description of the filter result.
     * @param toolType          The type of query identified (e.g., CONVERSATIONAL, COMPLEX).
     * @param processedMessage  The cleaned user message.
     */
    public record FilterResult(boolean canBeHandledByMcp, String reason, String toolType,
                               String processedMessage) {
    }
}
