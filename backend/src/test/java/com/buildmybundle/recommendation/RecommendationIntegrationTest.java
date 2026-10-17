package com.buildmybundle.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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

/** End-to-end recommendations on the seeded catalog, templates and compatibility rules. */
class RecommendationIntegrationTest extends IntegrationTest {

	private static final String HOME_OFFICE_ITEMS = """
			[{"categorySlug":"desks","kind":"REQUIRED"},{"categorySlug":"chairs","kind":"REQUIRED"},
			 {"categorySlug":"monitors","kind":"REQUIRED"},{"categorySlug":"keyboards","kind":"REQUIRED"},
			 {"categorySlug":"mice","kind":"REQUIRED"},{"categorySlug":"desk-lamps","kind":"OPTIONAL"}]""";

	@Test
	void homeOfficeGetsThreeDistinctCompatibleBundlesWithinBudget() throws Exception {
		String run = recommend(goal("home-office", 1200, HOME_OFFICE_ITEMS, null));

		assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("OK");
		List<Map<String, Object>> bundles = JsonPath.read(run, "$.bundles");
		assertThat(bundles).as(JsonPath.read(run, "$.notes").toString() + " tiers=" + JsonPath.read(run, "$.bundles[*].tier") + " totals=" + JsonPath.read(run, "$.bundles[*].totalPrice") + " skus=" + JsonPath.read(run, "$.bundles[*].items[*].sku")).hasSize(3);
		assertThat(JsonPath.<List<String>>read(run, "$.bundles[*].tier")).containsExactly("BUDGET", "BALANCED", "PREMIUM");
		List<Double> totals = JsonPath.read(run, "$.bundles[*].totalPrice");
		assertThat(totals).allSatisfy(t -> assertThat(t).isLessThanOrEqualTo(1200.0));
		assertThat(totals.get(0)).isLessThan(totals.get(1)).isLessThan(totals.get(2));
		assertThat(JsonPath.<List<Boolean>>read(run, "$.bundles[*].compatibility.compatible")).containsOnly(true);
		// every bundle covers all five must-haves; explanations come from catalog facts
		assertThat(JsonPath.<List<Object>>read(run, "$.bundles[0].items")).hasSize(5);
		assertThat(JsonPath.<String>read(run, "$.bundles[0].items[0].explanation")).contains("rated").contains("customers");
		assertThat(JsonPath.<String>read(run, "$.bundles[0].summary")).startsWith("The lowest-cost way to cover all 5 must-haves");
		assertThat(JsonPath.<String>read(run, "$.preferredTier")).isEqualTo("BALANCED");
		assertThat(JsonPath.<String>read(run, "$.stats.algorithmVersion")).isEqualTo("bnb-1.0");
	}

	@Test
	void photoStudioBundlesRespectLensMountsAndCardSlots() throws Exception {
		String run = recommend(goal("photo-studio", 3000, """
				[{"categorySlug":"cameras","kind":"REQUIRED"},{"categorySlug":"lenses","kind":"REQUIRED"},
				 {"categorySlug":"tripods","kind":"REQUIRED"},{"categorySlug":"studio-lighting","kind":"REQUIRED"},
				 {"categorySlug":"memory-cards","kind":"REQUIRED"}]""", null));

		List<Map<String, Object>> bundles = JsonPath.read(run, "$.bundles");
		assertThat(bundles).isNotEmpty();
		for (int i = 0; i < bundles.size(); i++) {
			assertThat(JsonPath.<Boolean>read(run, "$.bundles[" + i + "].compatibility.compatible")).isTrue();
			assertThat(JsonPath.<List<String>>read(run, "$.bundles[" + i + "].compatibility.passed[*].ruleCode"))
				.contains("LENS_MOUNT", "CARD_SLOT", "TRIPOD_LOAD");
		}
	}

	@Test
	void budgetPodcastBundleAvoidsXlrMicrophonesWithoutAnInterface() throws Exception {
		String run = recommend(goal("podcast-studio", 900, """
				[{"categorySlug":"microphones","kind":"REQUIRED"},{"categorySlug":"headphones","kind":"REQUIRED"},
				 {"categorySlug":"mic-arms","kind":"REQUIRED"},{"categorySlug":"audio-interfaces","kind":"OPTIONAL"}]""", null));

		// the budget tier buys must-haves only, so it must choose a microphone that also connects over USB
		List<String> budgetSkus = JsonPath.read(run, "$.bundles[0].items[*].sku");
		assertThat(JsonPath.<String>read(run, "$.bundles[0].tier")).isEqualTo("BUDGET");
		assertThat(budgetSkus).noneMatch(s -> s.startsWith("AIF-")).doesNotContain("MIC-003", "MIC-004");
		assertThat(JsonPath.<List<Boolean>>read(run, "$.bundles[*].compatibility.compatible")).containsOnly(true);
	}

	@Test
	void smartHomeBundlesStayInOneEcosystem() throws Exception {
		String run = recommend(goal("smart-home", 400, """
				[{"categorySlug":"smart-speakers","kind":"REQUIRED"},{"categorySlug":"smart-lighting","kind":"REQUIRED"},
				 {"categorySlug":"smart-plugs","kind":"OPTIONAL"},{"categorySlug":"video-doorbells","kind":"OPTIONAL"}]""", null));

		assertThat(JsonPath.<List<Boolean>>read(run, "$.bundles[*].compatibility.compatible")).containsOnly(true);
		assertThat(JsonPath.<List<String>>read(run, "$.bundles[*].compatibility.passed[*].ruleCode")).contains("LIGHTING_ECOSYSTEM");
	}

	@Test
	void onlyDesksThatFitTheRoomAreRecommended() throws Exception {
		String run = recommend(goal("home-office", 1500, HOME_OFFICE_ITEMS, "{\"widthCm\":110,\"depthCm\":60}"));

		// DSK-001 (100 x 50 cm) is the only desk that fits 110 x 60 cm
		assertThat(JsonPath.<List<String>>read(run, "$.bundles[*].items[?(@.categorySlug == 'desks')].sku"))
			.containsOnly("DSK-001");
	}

	@Test
	void tooSmallBudgetsGetTheMinimumCostAndConcreteSuggestions() throws Exception {
		String run = recommend(goal("home-office", 100, HOME_OFFICE_ITEMS, null));

		assertThat(JsonPath.<String>read(run, "$.status")).isEqualTo("INFEASIBLE");
		assertThat(JsonPath.<List<Object>>read(run, "$.bundles")).isEmpty();
		assertThat(JsonPath.<Double>read(run, "$.minimumFeasibleCost")).isEqualTo(415.0);
		assertThat(JsonPath.<List<String>>read(run, "$.reasons")).singleElement().asString().contains("$415.00");
		assertThat(JsonPath.<List<String>>read(run, "$.suggestions")).contains("Raise your budget to at least $415.00.");
	}

	@Test
	void ownedEquipmentIsNotBoughtButIsFlaggedForCompatibility() throws Exception {
		String run = recommend(goal("home-office", 1200, """
				[{"categorySlug":"monitors","kind":"OWNED"},{"categorySlug":"monitor-arms","kind":"REQUIRED"},
				 {"categorySlug":"desks","kind":"REQUIRED"}]""", null));

		assertThat(JsonPath.<List<String>>read(run, "$.bundles[*].items[*].categorySlug")).doesNotContain("monitors");
		assertThat(JsonPath.<List<String>>read(run, "$.notes")).anyMatch(n -> n.startsWith("You already own monitors"));
	}

	@Test
	void runsAndBundlesCanBeRevisitedAndBundlesAddedToTheCart() throws Exception {
		String goalId = goal("home-office", 1200, HOME_OFFICE_ITEMS, null);
		String run = recommend(goalId);
		String runId = JsonPath.read(run, "$.id");
		String bundleId = JsonPath.read(run, "$.bundles[0].id");

		mockMvc.perform(get("/api/v1/recommendations/" + runId)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(runId));
		mockMvc.perform(get("/api/v1/goals/" + goalId + "/recommendations/latest")).andExpect(jsonPath("$.id").value(runId));
		mockMvc.perform(get("/api/v1/bundles/" + bundleId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.purchasable").value(true))
			.andExpect(jsonPath("$.items", hasSize(5)));

		new BrowserSession(mockMvc).post("/api/v1/cart/bundles/" + bundleId).andExpect(status().isUnauthorized());
		BrowserSession customer = newCustomer();
		customer.post("/api/v1/cart/bundles/" + bundleId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(5)))
			.andExpect(jsonPath("$.checkoutReady").value(true));
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
		String goalId = goal("home-office", 1200, HOME_OFFICE_ITEMS, null);
		mockMvc.perform(get("/api/v1/goals/" + goalId + "/recommendations/latest"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NO_RECOMMENDATIONS"));
	}

	@Test
	void compatibilityRulesAreSeeded() throws Exception {
		new BrowserSession(mockMvc); // anonymous
		String run = recommend(goal(null, 2000, """
				[{"categorySlug":"monitors","kind":"REQUIRED"},{"categorySlug":"monitor-arms","kind":"REQUIRED"}]""", null));
		assertThat(JsonPath.<List<String>>read(run, "$.bundles[*].compatibility.passed[*].ruleCode"))
			.contains("MONITOR_ARM_LOAD", "MONITOR_ARM_SIZE", "MONITOR_ARM_VESA");
		mockMvc.perform(get("/api/v1/recommendations/" + JsonPath.read(run, "$.id")))
			.andExpect(jsonPath("$.bundles[*].tier", hasItem("BUDGET")));
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
