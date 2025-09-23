package com.example.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TextProcessingService {

	private static final int OPTIMAL_CHUNK_SIZE_WORDS = 200; // Approximate words
	private static final int CHUNK_OVERLAP_WORDS = 50;      // Approximate words
	private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
	private static final Pattern WORD_BOUNDARY = Pattern.compile("\\s+");

	/**
	 * Smartly chunks a given text into semantically coherent parts based on sentences.
	 *
	 * @param text The text to be chunked.
	 * @return A list of text chunks.
	 */
	public List<String> chunkText(String text) {
		if (text == null || text.trim().isEmpty()) {
			return Collections.emptyList();
		}

		String cleanedText = cleanText(text);
		String[] sentences = SENTENCE_BOUNDARY.split(cleanedText);

		if (sentences.length <= 1 && WORD_BOUNDARY.split(cleanedText).length <= OPTIMAL_CHUNK_SIZE_WORDS) {
			return List.of(cleanedText);
		}

		List<String> chunks = new ArrayList<>();
		StringBuilder currentChunk = new StringBuilder();
		List<String> wordsInCurrentChunk = new ArrayList<>();

		for (String sentence : sentences) {
			sentence = sentence.trim();
			if (sentence.isEmpty())
				continue;

			String[] sentenceWords = WORD_BOUNDARY.split(sentence);

			if (!currentChunk.isEmpty()
					&& wordsInCurrentChunk.size() + sentenceWords.length > OPTIMAL_CHUNK_SIZE_WORDS) {
				// Finalize the current chunk
				chunks.add(currentChunk.toString().trim());

				// Start a new chunk with overlap
				int overlapStartIndex = Math.max(0, wordsInCurrentChunk.size() - CHUNK_OVERLAP_WORDS);
				List<String> overlapWords = wordsInCurrentChunk.subList(overlapStartIndex, wordsInCurrentChunk.size());

				currentChunk = new StringBuilder(String.join(" ", overlapWords));
				if (!overlapWords.isEmpty()) {
					currentChunk.append(" ");
				}
				wordsInCurrentChunk = new ArrayList<>(overlapWords);
			}

			currentChunk.append(sentence).append(" ");
			wordsInCurrentChunk.addAll(Arrays.asList(sentenceWords));
		}

		if (!currentChunk.isEmpty()) {
			chunks.add(currentChunk.toString().trim());
		}

		if (chunks.isEmpty() && !cleanedText.isEmpty()) {
			chunks.add(cleanedText);
		}

		log.debug("Split text of length {} into {} chunks.", cleanedText.length(), chunks.size());
		return chunks;
	}

	private String cleanText(String text) {
		return text.replaceAll("[\\r\\n\\t]+", " ") // Replace newlines/tabs with space
				.replaceAll("\\s+", " ")          // Collapse multiple whitespaces
				.trim();
	}
}
