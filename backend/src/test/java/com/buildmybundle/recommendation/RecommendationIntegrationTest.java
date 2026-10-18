package com.buildmybundle.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

/** End-to-end outfit recommendations on the seeded fashion catalog and templates. */
class RecommendationIntegrationTest extends IntegrationTest {

	private static final String WEEKEND_MEN_ITEMS = """
			[{"categorySlug":"men-tshirts","kind":"REQUIRED"},{"categorySlug":"men-jeans","kind":"REQUIRED"},
			 {"categorySlug":"men-jackets","kind":"OPTIONAL"},{"categorySlug":"sunglasses","kind":"OPTIONAL"}]""";

	@Test
	void weekendLookGetsThreeDistinctOutfitsWithinBudget() throws Exception {
		String run = recommend(goal("weekend-casual-men", 300, WEEKEND_MEN_ITEMS, null));

		assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("OK");
		assertThat(JsonPath.<List<String>>read(run, "$.bundles[*].tier")).containsExactly("BUDGET", "BALANCED", "PREMIUM");
		List<Double> totals = JsonPath.read(run, "$.bundles[*].totalPrice");
		assertThat(totals).allSatisfy(t -> assertThat(t).isLessThanOrEqualTo(300.0));
		assertThat(totals.get(0)).isLessThan(totals.get(1)).isLessThan(totals.get(2));
		// every outfit has a tee and jeans; the budget tier buys must-haves only
		for (int i = 0; i < 3; i++) {
			assertThat(JsonPath.<List<String>>read(run, "$.bundles[" + i + "].items[*].categorySlug"))
				.contains("men-tshirts", "men-jeans")
				.doesNotHaveDuplicates();
		}
		assertThat(JsonPath.<List<Object>>read(run, "$.bundles[0].items")).hasSize(2);
		assertThat(JsonPath.<String>read(run, "$.bundles[0].items[0].explanation")).contains("rated");
		assertThat(JsonPath.<String>read(run, "$.preferredTier")).isEqualTo("BALANCED");
	}

	@Test
	void tooSmallBudgetsGetTheMinimumCostAndConcreteSuggestions() throws Exception {
		String run = recommend(goal("weekend-casual-men", 20, WEEKEND_MEN_ITEMS, null));

		assertThat(JsonPath.<String>read(run, "$.status")).isEqualTo("INFEASIBLE");
		assertThat(JsonPath.<List<Object>>read(run, "$.bundles")).isEmpty();
		assertThat(JsonPath.<Double>read(run, "$.minimumFeasibleCost")).isEqualTo(42.99);
		assertThat(JsonPath.<List<String>>read(run, "$.suggestions")).contains("Raise your budget to at least $42.99.");
	}

	@Test
	void ownedPiecesAreNotBought() throws Exception {
		String run = recommend(goal("weekend-casual-men", 200, """
				[{"categorySlug":"men-jeans","kind":"OWNED"},{"categorySlug":"men-tshirts","kind":"REQUIRED"},
				 {"categorySlug":"men-jackets","kind":"REQUIRED"}]""", null));

		assertThat(JsonPath.<List<String>>read(run, "$.bundles[*].items[*].categorySlug"))
			.contains("men-tshirts", "men-jackets")
			.doesNotContain("men-jeans");
	}

	@Test
	void runsAndOutfitsCanBeRevisitedAndNeedSizesBeforeTheCart() throws Exception {
		String goalId = goal("weekend-casual-men", 300, WEEKEND_MEN_ITEMS, null);
		String run = recommend(goalId);
		String runId = JsonPath.read(run, "$.id");
		String bundleId = JsonPath.read(run, "$.bundles[0].id");

		mockMvc.perform(get("/api/v1/recommendations/" + runId)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(runId));
		mockMvc.perform(get("/api/v1/goals/" + goalId + "/recommendations/latest")).andExpect(jsonPath("$.id").value(runId));
		mockMvc.perform(get("/api/v1/bundles/" + bundleId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)));

		new BrowserSession(mockMvc).post("/api/v1/cart/bundles/" + bundleId).andExpect(status().isUnauthorized());
		// apparel comes in sizes: the outfit cannot go to the cart until each piece has a size
		newCustomer().post("/api/v1/cart/bundles/" + bundleId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SIZE_REQUIRED"))
			.andExpect(jsonPath("$.productIds", hasSize(2)));
	}

	@Test
	void unknownIdsReturn404() throws Exception {
		mockMvc.perform(get("/api/v1/recommendations/00000000-0000-0000-0000-000000000000"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/bundles/00000000-0000-0000-0000-000000000000"))
			.andExpect(jsonPath("$.code").value("BUNDLE_NOT_FOUND"));
		new BrowserSession(mockMvc).post("/api/v1/goals/00000000-0000-0000-0000-000000000000/recommendations")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
		String goalId = goal("weekend-casual-men", 300, WEEKEND_MEN_ITEMS, null);
		mockMvc.perform(get("/api/v1/goals/" + goalId + "/recommendations/latest"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NO_RECOMMENDATIONS"));
	}

	private String goal(String template, int budget, String items, String space) throws Exception {
		String body = "{" + (template == null ? "" : "\"templateSlug\":\"" + template + "\",") + "\"budget\":" + budget
				+ ",\"quality\":\"BALANCED\",\"items\":" + items + (space == null ? "" : ",\"space\":" + space) + "}";
		String response = new BrowserSession(mockMvc).postJson("/api/v1/goals", body)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String recommend(String goalId) throws Exception {
		return new BrowserSession(mockMvc).post("/api/v1/goals/" + goalId + "/recommendations")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}
}
