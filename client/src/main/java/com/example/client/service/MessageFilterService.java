package com.example.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MessageFilterService {
	private static final List<Pattern> GRADE_PATTERNS = List.of(
			Pattern.compile("(?i).*(grade|gpa|score|mark|result).*", Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*(calculate|compute|average|mean).*gpa.*", Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*what.*grade.*(student|course).*", Pattern.CASE_INSENSITIVE));

	private static final List<Pattern> STATISTICS_PATTERNS = List.of(
			Pattern.compile("(?i).*(statistic|analytics|report|summary).*", Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*(how many|count|total|number of).*(student|course|research).*",
					Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*(average|mean|median|distribution).*", Pattern.CASE_INSENSITIVE));

	private static final List<Pattern> ACADEMIC_SEARCH_PATTERNS = List.of(
			Pattern.compile("(?i).*(search|find|look for|locate).*(paper|research|publication|article).*",
					Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*(research|paper|publication|article|study|thesis|dissertation).*(about|on).*",
					Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*(citation|reference|bibliography|scholarly|academic).*", Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*(literature review|research database|academic database).*",
					Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*find.*(author|researcher|scholar).*work.*", Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*(journal|conference|proceeding|manuscript).*search.*", Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?i).*what.*research.*(available|exist|done).*(topic|subject|field).*",
					Pattern.CASE_INSENSITIVE));

	public FilterResult filterMessage(String message) {
		if (message == null || message.trim().isEmpty()) {
			return new FilterResult(false, "Empty message", "NONE", message);
		}

		String trimmedMessage = message.trim();
		log.info("Filtering message: {}",
				trimmedMessage.length() > 50 ? trimmedMessage.substring(0, 50) + "..." : trimmedMessage);

		boolean isInternalResearchQuery = containsInternalResearchIndicators(trimmedMessage);

		if (!isInternalResearchQuery) {
			for (Pattern pattern : ACADEMIC_SEARCH_PATTERNS) {
				if (pattern.matcher(trimmedMessage).matches()) {
					log.info("External academic search query detected.");
					return new FilterResult(true, "Academic database search needed", "ACADEMIC_SEARCH",
							"Please perform an external academic database search for: " + trimmedMessage);
				}
			}
		} else {
			log.info("Internal university research query detected, routing to RAG.");
		}

		for (Pattern pattern : GRADE_PATTERNS) {
			if (pattern.matcher(trimmedMessage).matches()) {
				log.info("Grade query detected.");
				return new FilterResult(true, "Grade calculation needed", "GRADE",
						"Please use grade calculation tools for: " + trimmedMessage);
			}
		}

		for (Pattern pattern : STATISTICS_PATTERNS) {
			if (pattern.matcher(trimmedMessage).matches()) {
				log.info("Statistics query detected.");
				return new FilterResult(true, "Statistics calculation needed", "STATISTICS",
						"Please use statistics tools for: " + trimmedMessage);
			}
		}

		log.info("Regular RAG query detected, no specific MCP tools needed.");
		return new FilterResult(false, "Regular query", "RAG", trimmedMessage);
	}

	private boolean containsInternalResearchIndicators(String message) {
		String lowerMessage = message.toLowerCase();

		boolean hasStrongInternalIndicators =
				lowerMessage.contains("university research") || lowerMessage.contains("internal research")
						|| lowerMessage.contains("local research") || lowerMessage.matches(
						".*research\\s+id\\s*:?\\s*[a-z0-9]+.*") || // "research id: RES001"
						lowerMessage.matches(".*\\b(res|proj)\\d{3,}\\b.*"); // ID patterns like RES001, PROJ123

		boolean hasLookupTerms = lowerMessage.contains("find research by") || lowerMessage.contains("show me research")
				|| lowerMessage.contains("list research") || lowerMessage.contains("get research")
				|| lowerMessage.matches(
				".*research.*(author|title|keyword|year)\\s*:.*"); // "research by author: Smith"

		boolean hasExternalIndicators =
				lowerMessage.contains("search pubmed") || lowerMessage.contains("search google scholar")
						|| lowerMessage.contains("search ieee") || lowerMessage.contains("search arxiv")
						|| lowerMessage.contains("latest research") || lowerMessage.contains("recent publications")
						|| lowerMessage.contains("peer reviewed") || lowerMessage.matches(
						".*research.*(journal|conference|proceeding|citation|doi)\\b.*");

		if (hasExternalIndicators) {
			return false; // Definitely external
		}

		// If it has strong internal indicators or lookup terms, it's likely internal.
		return hasStrongInternalIndicators || hasLookupTerms;
	}

	public record FilterResult(boolean canBeHandledByMcp,
			String reason,
			String toolType,
			String processedMessage) {
	}
}
