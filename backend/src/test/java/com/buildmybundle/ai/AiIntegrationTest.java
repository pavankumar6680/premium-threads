package com.buildmybundle.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** AI features end to end, with a scripted model instead of Ollama. */
@Import(AiIntegrationTest.FakeModelConfig.class)
class AiIntegrationTest extends IntegrationTest {

	@Autowired
	FakeLanguageModel model;

	@BeforeEach
	void reset() {
		model.prompts.clear();
		model.responder = (system, user) -> Optional.empty();
	}

	@Test
	void reportsTheModelStatus() throws Exception {
		mockMvc.perform(get("/api/v1/ai/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.model").value("fake-model"));
	}

	@Test
	void mergesTheModelsValidatedReadingWithTheRules() throws Exception {
		model.responder = (system, user) -> Optional.of("""
				{"templateSlug":"gaming-setup","budget":2000,"quality":"BALANCED",
				 "categories":[{"slug":"monitors","kind":"OWNED"},{"slug":"rgb-fans","kind":"REQUIRED"},{"slug":"webcams","kind":"OPTIONAL"}],
				 "usageTags":["streaming"],"preferredBrands":["corvex","Razer"],
				 "clarifyingQuestions":["Do you also need a desk?"]}""");

		new BrowserSession(mockMvc)
			.postJson("/api/v1/goals/analyze", "{\"text\":\"Something for streaming my games, about 2000 dollars. Got a monitor from my brother.\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("AI"))
			.andExpect(jsonPath("$.templateSlug").value("gaming-setup"))
			.andExpect(jsonPath("$.budget").value(2000.0))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'monitors')].kind").value("OWNED"))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'webcams')].kind").value("OPTIONAL"))
			.andExpect(jsonPath("$.items[*].categorySlug", org.hamcrest.Matchers.not(hasItem("rgb-fans"))))
			.andExpect(jsonPath("$.preferredBrands[0]").value("Corvex"))
			.andExpect(jsonPath("$.preferredBrands.length()").value(1))
			.andExpect(jsonPath("$.clarifications", hasItem("Do you also need a desk?")));
		// the model is told the store's real vocabulary
		assertThat(model.prompts.getFirst()).contains("gaming-setup", "monitor-arms", "Corvex");
	}

	@Test
	void fallsBackToTheRulesWhenTheModelGivesNoUsableAnswer() throws Exception {
		model.responder = (system, user) -> Optional.of("Sorry, I can't do that.");

		new BrowserSession(mockMvc).postJson("/api/v1/goals/analyze", "{\"text\":\"home office for $1,200 with a desk\"}")
			.andExpect(jsonPath("$.source").value("RULES"))
			.andExpect(jsonPath("$.templateSlug").value("home-office"))
			.andExpect(jsonPath("$.budget").value(1200.0));
	}

	@Test
	void writesGroundedOverviewsCachesThemAndRejectsInventedNumbers() throws Exception {
		String run = recommendationRun();
		String runId = JsonPath.read(run, "$.id");
		String budgetTotal = JsonPath.<List<Object>>read(run, "$.bundles[*].totalPrice").getFirst().toString();

		model.responder = (system, user) -> Optional.of("The budget bundle at $99,999 is the best deal you will ever see in your life.");
		BrowserSession browser = new BrowserSession(mockMvc);
		browser.post("/api/v1/recommendations/" + runId + "/explanation")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));

		String grounded = "The budget bundle costs $" + budgetTotal
				+ " and covers every must-have, while the premium option spends more on higher-rated products.";
		model.responder = (system, user) -> Optional.of("**" + grounded + "**");
		browser.post("/api/v1/recommendations/" + runId + "/explanation")
			.andExpect(jsonPath("$.status").value("OK"))
			.andExpect(jsonPath("$.text").value(grounded))
			.andExpect(jsonPath("$.cached").value(false));
		assertThat(model.prompts.getLast()).contains("Budget bundle: total");

		model.responder = (system, user) -> { throw new AssertionError("cached overviews must not call the model"); };
		browser.post("/api/v1/recommendations/" + runId + "/explanation")
			.andExpect(jsonPath("$.status").value("OK"))
			.andExpect(jsonPath("$.cached").value(true));
	}

	@Test
	void reportsWhenTheModelDoesNotAnswer() throws Exception {
		String runId = JsonPath.read(recommendationRun(), "$.id");
		new BrowserSession(mockMvc).post("/api/v1/recommendations/" + runId + "/explanation")
			.andExpect(jsonPath("$.status").value("UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("The AI model did not answer in time. Please try again."));
	}

	private String recommendationRun() throws Exception {
		BrowserSession browser = new BrowserSession(mockMvc);
		String goal = browser.postJson("/api/v1/goals", """
				{"templateSlug":"home-office","budget":1200,"quality":"BALANCED",
				 "items":[{"categorySlug":"desks","kind":"REQUIRED"},{"categorySlug":"chairs","kind":"REQUIRED"}]}""")
			.andReturn().getResponse().getContentAsString();
		return browser.post("/api/v1/goals/" + JsonPath.read(goal, "$.id") + "/recommendations")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
	}

	static final class FakeLanguageModel implements LanguageModel {

		final List<String> prompts = new ArrayList<>();
		volatile BiFunction<String, String, Optional<String>> responder = (s, u) -> Optional.empty();

		@Override
		public AiStatus status() {
			return new AiStatus(true, true, "fake-model", "Using fake-model.");
		}

		@Override
		public Optional<String> complete(String systemPrompt, String userPrompt, Format format) {
			prompts.add(userPrompt);
			return responder.apply(systemPrompt, userPrompt);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeModelConfig {

		@Bean
		@Primary
		FakeLanguageModel fakeLanguageModel() {
			return new FakeLanguageModel();
		}
	}
}
