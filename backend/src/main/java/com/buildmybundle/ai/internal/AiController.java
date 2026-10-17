package com.buildmybundle.ai.internal;

import com.buildmybundle.ai.LanguageModel;
import com.buildmybundle.ai.LanguageModel.AiStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI")
@RestController
class AiController {

	private final LanguageModel languageModel;

	AiController(LanguageModel languageModel) {
		this.languageModel = languageModel;
	}

	@Operation(summary = "Whether optional AI assistance is enabled and the local model is available")
	@GetMapping("/api/v1/ai/status")
	AiStatus status() {
		return languageModel.status();
	}
}
