package com.buildmybundle.ai.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.buildmybundle.ai.LanguageModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ollama through Spring AI. Availability is probed via Ollama's {@code /api/tags} (cached briefly), every call
 * runs with a hard timeout on a virtual thread, and any failure is logged and reported as "no answer".
 */
@Component
class OllamaLanguageModel implements LanguageModel, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(OllamaLanguageModel.class);
	private static final Duration STATUS_TTL = Duration.ofSeconds(30);
	private static final Duration PROBE_TIMEOUT = Duration.ofMillis(1500);
	private static final int MAX_OUTPUT_TOKENS = 350;

	private final AiProperties properties;
	private final ObjectProvider<ChatModel> chatModel;
	private final String baseUrl;
	private final String model;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private volatile AiStatus cachedStatus;
	private volatile Instant checkedAt = Instant.EPOCH;

	OllamaLanguageModel(AiProperties properties, ObjectProvider<ChatModel> chatModel,
			@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
			@Value("${spring.ai.ollama.chat.model:llama3.2:3b}") String model) {
		this.properties = properties;
		this.chatModel = chatModel;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.model = model;
	}

	@Override
	public AiStatus status() {
		if (!properties.enabled()) {
			return new AiStatus(false, false, model,
					"AI assistance is off. Set AI_ENABLED=true and run Ollama to turn it on.");
		}
		if (cachedStatus == null || Instant.now().isAfter(checkedAt.plus(STATUS_TTL))) {
			cachedStatus = probe();
			checkedAt = Instant.now();
		}
		return cachedStatus;
	}

	private AiStatus probe() {
		try {
			HttpResponse<String> response = http.send(
					HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags")).timeout(PROBE_TIMEOUT).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return new AiStatus(true, false, model, "Ollama answered with HTTP " + response.statusCode() + ".");
			}
			String bareModel = model.contains(":") ? model : model + ":latest";
			boolean installed = response.body().contains("\"" + model + "\"") || response.body().contains("\"" + bareModel + "\"");
			return installed ? new AiStatus(true, true, model, "Using " + model + " on Ollama.")
					: new AiStatus(true, false, model, "Ollama is running but the model is missing. Run: ollama pull " + model);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return new AiStatus(true, false, model, "Ollama status check was interrupted.");
		}
		catch (Exception ex) {
			return new AiStatus(true, false, model, "Ollama is not reachable at " + baseUrl + ".");
		}
	}

	@Override
	public Optional<String> complete(String systemPrompt, String userPrompt, Format format) {
		ChatModel chat = chatModel.getIfAvailable();
		if (chat == null || !status().available()) {
			return Optional.empty();
		}
		// Per-request options replace the configured defaults, so the model must be set here explicitly
		// (otherwise Spring AI falls back to its built-in default model).
		OllamaChatOptions.Builder builder = OllamaChatOptions.builder();
		builder.model(model);
		builder.numPredict(MAX_OUTPUT_TOKENS); // bounded answers keep CPU inference time predictable
		if (format == Format.JSON) {
			builder.format("json"); // Ollama's JSON mode guarantees syntactically valid JSON
		}
		Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)), builder.build());
		CompletableFuture<String> call = CompletableFuture.supplyAsync(() -> {
			ChatResponse response = chat.call(prompt);
			return response.getResult() == null ? null : response.getResult().getOutput().getText();
		}, executor);
		long started = System.nanoTime();
		try {
			String text = call.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
			log.debug("AI call ({}) answered in {} ms", format, (System.nanoTime() - started) / 1_000_000);
			return Optional.ofNullable(text).filter(t -> !t.isBlank());
		}
		catch (TimeoutException ex) {
			call.cancel(true);
			log.warn("AI call timed out after {}", properties.timeout());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		catch (Exception ex) {
			log.warn("AI call failed: {}", ex.getMessage());
			checkedAt = Instant.EPOCH; // re-probe next time
		}
		return Optional.empty();
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}
}
