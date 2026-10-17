package com.buildmybundle.ai;

import java.util.Optional;

/**
 * Public API of the ai module: a local language model that may or may not be running. Callers must always
 * have a non-AI fallback: {@link #complete} returns empty when AI is disabled, unreachable, too slow or fails.
 */
public interface LanguageModel {

	enum Format {
		TEXT, JSON
	}

	AiStatus status();

	Optional<String> complete(String systemPrompt, String userPrompt, Format format);

	/**
	 * @param enabled AI assistance switched on in configuration
	 * @param available enabled, the model server is reachable and the model is installed
	 * @param message human-readable state, e.g. how to start Ollama
	 */
	record AiStatus(boolean enabled, boolean available, String model, String message) {
	}
}
