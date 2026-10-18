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
	void mergesOnlyTheModelsGroundedReadingWithTheRules() throws Exception {
		model.responder = (system, user) -> Optional.of("""
				{"templateSlug":"festive-men","budget":200,"quality":"PREMIUM",
				 "categories":[{"slug":"watches","kind":"OWNED"},{"slug":"rgb-fans","kind":"REQUIRED"},
				               {"slug":"sunglasses","kind":"REQUIRED"},{"slug":"wallets","kind":"OWNED"}],
				 "usageTags":["formal"],"preferredBrands":["guess"],
				 "clarifyingQuestions":["Do you also need shoes?"]}""");

		new BrowserSession(mockMvc)
			.postJson("/api/v1/goals/analyze",
					"{\"text\":\"Something for my cousin's mehndi for him, about 200 dollars. My old watch still works.\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("AI"))
			.andExpect(jsonPath("$.templateSlug").value("festive-men")) // the rules found no occasion
			.andExpect(jsonPath("$.budget").value(200.0))
			.andExpect(jsonPath("$.quality").value("BALANCED")) // the shopper never asked for premium
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'watches')].kind").value("OWNED"))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'wallets')].kind",
					org.hamcrest.Matchers.not(hasItem("OWNED")))) // never mentioned
			.andExpect(jsonPath("$.items[*].categorySlug", org.hamcrest.Matchers.not(hasItem("rgb-fans"))))
			.andExpect(jsonPath("$.preferredBrands.length()").value(0))
			.andExpect(jsonPath("$.clarifications", org.hamcrest.Matchers.not(hasItem("Do you also need shoes?"))));
		// the model is told the store's real vocabulary
		assertThat(model.prompts.getFirst()).contains("festive-men", "women-dresses").doesNotContain("monitor");
	}

	@Test
	void fallsBackToTheRulesWhenTheModelGivesNoUsableAnswer() throws Exception {
		model.responder = (system, user) -> Optional.of("Sorry, I can't do that.");

		new BrowserSession(mockMvc).postJson("/api/v1/goals/analyze", "{\"text\":\"office outfit for men, $300, with a shirt\"}")
			.andExpect(jsonPath("$.source").value("RULES"))
			.andExpect(jsonPath("$.templateSlug").value("office-men"))
			.andExpect(jsonPath("$.budget").value(300.0));
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
				{"templateSlug":"weekend-casual-men","budget":300,"quality":"BALANCED",
				 "items":[{"categorySlug":"men-tshirts","kind":"REQUIRED"},{"categorySlug":"men-jeans","kind":"REQUIRED"}]}""")
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
