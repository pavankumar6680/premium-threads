package com.buildmybundle.bundles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** The bundle workspace on the seeded catalog: customize, swap, lock, add, budget, re-optimize, save. */
class BundleWorkspaceIntegrationTest extends IntegrationTest {

	private static final String OFFICE = """
			{"templateSlug":"home-office","budget":1200,"quality":"BALANCED","items":[
			 {"categorySlug":"desks","kind":"REQUIRED"},{"categorySlug":"chairs","kind":"REQUIRED"},
			 {"categorySlug":"monitors","kind":"REQUIRED"}]}""";
	private static final String PHOTO = """
			{"templateSlug":"photo-studio","budget":3000,"quality":"BALANCED","items":[
			 {"categorySlug":"cameras","kind":"REQUIRED"},{"categorySlug":"lenses","kind":"REQUIRED"},
			 {"categorySlug":"memory-cards","kind":"REQUIRED"}]}""";

	private BrowserSession customer;

	@BeforeEach
	void signIn() throws Exception {
		customer = newCustomer();
	}

	@Test
	void customizingCreatesAnEditableDraftOnlyTheOwnerCanChange() throws Exception {
		String recommended = recommendedBundle(OFFICE, 1);
		new BrowserSession(mockMvc).post("/api/v1/bundles/" + recommended + "/customize").andExpect(status().isUnauthorized());

		String draft = customize(recommended);
		customer.get("/api/v1/bundles/" + draft)
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.editable").value(true))
			.andExpect(jsonPath("$.sourceBundleId").value(recommended))
			.andExpect(jsonPath("$.goalTitle").value("Professional home office"))
			.andExpect(jsonPath("$.items.length()").value(3));
		customer.get("/api/v1/bundles/" + recommended).andExpect(jsonPath("$.editable").value(false));

		BrowserSession stranger = newCustomer();
		stranger.get("/api/v1/bundles/" + draft).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false));
		stranger.patchJson("/api/v1/bundles/" + draft + "/items/desks", "{\"locked\":true}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BUNDLE_NOT_FOUND"));
	}

	@Test
	void recommendedBundlesAreReadOnly() throws Exception {
		String recommended = recommendedBundle(OFFICE, 0);
		customer.patchJson("/api/v1/bundles/" + recommended + "/items/desks", "{\"locked\":true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BUNDLE_READ_ONLY"));
	}

	@Test
	void alternativesShowPriceChangesAndCompatibilityAndIncompatibleBundlesCannotBeBought() throws Exception {
		String draft = customize(recommendedBundle(PHOTO, 1));
		String body = customer.get("/api/v1/bundles/" + draft + "/alternatives?category=lenses")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].current").value(true))
			.andExpect(jsonPath("$[0].priceDelta").value(0.0))
			.andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> alternatives = JsonPath.read(body, "$");
		Map<String, Object> wrongMount = alternatives.stream()
			.filter(a -> !(Boolean) a.get("compatible"))
			.findFirst()
			.orElseThrow();
		assertThat((List<String>) wrongMount.get("issues")).contains("The lens does not fit the camera's lens mount.");
		Map<String, Object> rightMount = alternatives.stream()
			.filter(a -> (Boolean) a.get("compatible") && !(Boolean) a.get("current") && (Boolean) a.get("inStock"))
			.findFirst()
			.orElseThrow();

		customer.putJson("/api/v1/bundles/" + draft + "/items/lenses", "{\"productId\":" + wrongMount.get("productId") + "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.compatibility.compatible").value(false))
			.andExpect(jsonPath("$.compatibility.violations[*].ruleCode", hasItem("LENS_MOUNT")))
			.andExpect(jsonPath("$.purchasable").value(false))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'lenses')].explanation").value("Chosen by you."));
		customer.post("/api/v1/cart/bundles/" + draft)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BUNDLE_INCOMPATIBLE"));

		customer.putJson("/api/v1/bundles/" + draft + "/items/lenses", "{\"productId\":" + rightMount.get("productId") + "}")
			.andExpect(jsonPath("$.compatibility.compatible").value(true))
			.andExpect(jsonPath("$.purchasable").value(true));
		customer.putJson("/api/v1/bundles/" + draft + "/items/lenses", "{\"productId\":1}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("WRONG_CATEGORY"));
	}

	@Test
	void accessoriesIncludeTheTemplatesOtherCategories() throws Exception {
		String draft = customize(recommendedBundle(PHOTO, 0));
		customer.get("/api/v1/bundles/" + draft + "/accessories")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.categorySlug == 'studio-lighting')].reason")
				.value("Popular for photography studio setups"))
			.andExpect(jsonPath("$[*].categorySlug", hasItem("tripods"))); // rule-linked to the camera
	}

	@Test
	void quantitiesAndLocksUpdateTotals() throws Exception {
		String draft = customize(recommendedBundle(OFFICE, 0));
		String before = customer.get("/api/v1/bundles/" + draft).andReturn().getResponse().getContentAsString();
		double total = ((Number) JsonPath.read(before, "$.totalPrice")).doubleValue();
		double deskPrice = ((Number) JsonPath.<List<Number>>read(before, "$.items[?(@.categorySlug == 'desks')].currentPrice").getFirst()).doubleValue();

		customer.patchJson("/api/v1/bundles/" + draft + "/items/desks", "{\"quantity\":2,\"locked\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'desks')].quantity").value(2))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'desks')].locked").value(true))
			.andExpect(jsonPath("$.totalPrice").value(total + deskPrice));
		customer.patchJson("/api/v1/bundles/" + draft + "/items/desks", "{\"quantity\":11}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void accessoriesAreCompatibleSuggestionsAndOnlyOptionalItemsCanBeRemoved() throws Exception {
		String draft = customize(recommendedBundle(OFFICE, 1));
		String body = customer.get("/api/v1/bundles/" + draft + "/accessories")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.categorySlug == 'monitor-arms')].reason", hasItem(startsWith("Works with your"))))
			.andExpect(jsonPath("$[*].products[*].compatible", everyItem(org.hamcrest.Matchers.is(true))))
			.andReturn().getResponse().getContentAsString();
		Number armId = JsonPath.<List<Number>>read(body, "$[?(@.categorySlug == 'monitor-arms')].products[0].productId").getFirst();

		customer.postJson("/api/v1/bundles/" + draft + "/items", "{\"productId\":" + armId + "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(4))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'monitor-arms')].optional").value(true))
			.andExpect(jsonPath("$.compatibility.passed[*].ruleCode", hasItem("MONITOR_ARM_LOAD")));
		customer.postJson("/api/v1/bundles/" + draft + "/items", "{\"productId\":" + armId + "}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_IN_BUNDLE"));

		customer.delete("/api/v1/bundles/" + draft + "/items/desks")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REQUIRED_ITEM"));
		customer.delete("/api/v1/bundles/" + draft + "/items/monitor-arms")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3));
	}

	@Test
	void reoptimizingKeepsLockedItemsAndRespectsTheNewBudget() throws Exception {
		String draft = customize(recommendedBundle(OFFICE, 0));
		String before = customer.get("/api/v1/bundles/" + draft).andReturn().getResponse().getContentAsString();
		Number lockedDesk = JsonPath.<List<Number>>read(before, "$.items[?(@.categorySlug == 'desks')].productId").getFirst();
		customer.patchJson("/api/v1/bundles/" + draft + "/items/desks", "{\"locked\":true}").andExpect(status().isOk());

		customer.putJson("/api/v1/bundles/" + draft + "/budget", "{\"budget\":3000}")
			.andExpect(jsonPath("$.budget").value(3000.0));
		// the draft is the Budget bundle: with the same strategy it stays cheap, so ask for best quality
		customer.post("/api/v1/bundles/" + draft + "/reoptimize").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalPrice").value(((Number) JsonPath.read(before, "$.totalPrice")).doubleValue()));
		String after = customer.post("/api/v1/bundles/" + draft + "/reoptimize?strategy=premium")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.compatibility.compatible").value(true))
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<Number>>read(after, "$.items[?(@.categorySlug == 'desks')].productId").getFirst().longValue())
			.isEqualTo(lockedDesk.longValue());
		assertThat(((Number) JsonPath.read(after, "$.totalPrice")).doubleValue()).isLessThanOrEqualTo(3000.0)
			.isGreaterThan(((Number) JsonPath.read(before, "$.totalPrice")).doubleValue());

		customer.putJson("/api/v1/bundles/" + draft + "/budget", "{\"budget\":100}").andExpect(status().isOk());
		customer.post("/api/v1/bundles/" + draft + "/reoptimize")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REOPTIMIZE_INFEASIBLE"))
			.andExpect(jsonPath("$.minimumFeasibleCost").isNumber())
			.andExpect(jsonPath("$.suggestions[0]", startsWith("Raise the budget to at least")));
		customer.post("/api/v1/bundles/" + draft + "/reoptimize?strategy=cheapest")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_STRATEGY"));
	}

	@Test
	void savedBundlesAreListedAndCanBeDeleted() throws Exception {
		String draft = customize(recommendedBundle(OFFICE, 2));
		customer.postJson("/api/v1/bundles/" + draft + "/save", "{\"name\":\"My dream office\"}")
			.andExpect(jsonPath("$.status").value("SAVED"))
			.andExpect(jsonPath("$.name").value("My dream office"))
			.andExpect(jsonPath("$.editable").value(true));
		customer.get("/api/v1/me/bundles")
			.andExpect(jsonPath("$[0].id").value(draft))
			.andExpect(jsonPath("$[0].status").value("SAVED"))
			.andExpect(jsonPath("$[0].goalTitle").value("Professional home office"));

		customer.delete("/api/v1/bundles/" + draft).andExpect(status().isNoContent());
		customer.get("/api/v1/bundles/" + draft).andExpect(status().isNotFound());
		customer.get("/api/v1/me/bundles").andExpect(jsonPath("$.length()").value(0));
	}

	// ------------------------------------------------------------------ helpers

	/** Generates recommendations for a new goal and returns the id of the bundle at {@code index}. */
	private String recommendedBundle(String goalJson, int index) throws Exception {
		String goal = customer.postJson("/api/v1/goals", goalJson).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String run = customer.post("/api/v1/goals/" + JsonPath.read(goal, "$.id") + "/recommendations")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(run, "$.bundles[*].id");
		return ids.get(Math.min(index, ids.size() - 1));
	}

	private String customize(String bundleId) throws Exception {
		ResultActions result = customer.post("/api/v1/bundles/" + bundleId + "/customize").andExpect(status().isCreated());
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}
}
