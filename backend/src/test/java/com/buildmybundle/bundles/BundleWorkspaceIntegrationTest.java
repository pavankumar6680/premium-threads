package com.buildmybundle.bundles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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

/** The outfit workspace on the seeded catalog: customize, swap, lock, add, budget, re-optimize, save. */
class BundleWorkspaceIntegrationTest extends IntegrationTest {

	private static final String OUTFIT = """
			{"templateSlug":"weekend-casual-men","budget":300,"quality":"BALANCED","items":[
			 {"categorySlug":"men-tshirts","kind":"REQUIRED"},{"categorySlug":"men-jeans","kind":"REQUIRED"},
			 {"categorySlug":"men-jackets","kind":"REQUIRED"}]}""";

	private BrowserSession customer;

	@BeforeEach
	void signIn() throws Exception {
		customer = newCustomer();
	}

	@Test
	void customizingCreatesAnEditableDraftOnlyTheOwnerCanChange() throws Exception {
		String recommended = recommendedBundle(OUTFIT, 1);
		new BrowserSession(mockMvc).post("/api/v1/bundles/" + recommended + "/customize").andExpect(status().isUnauthorized());

		String draft = customize(recommended);
		customer.get("/api/v1/bundles/" + draft)
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.editable").value(true))
			.andExpect(jsonPath("$.sourceBundleId").value(recommended))
			.andExpect(jsonPath("$.goalTitle").value("Weekend casual (men)"))
			.andExpect(jsonPath("$.items.length()").value(3));
		customer.get("/api/v1/bundles/" + recommended).andExpect(jsonPath("$.editable").value(false));

		BrowserSession stranger = newCustomer();
		stranger.get("/api/v1/bundles/" + draft).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false));
		stranger.patchJson("/api/v1/bundles/" + draft + "/items/men-jeans", "{\"locked\":true}")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BUNDLE_NOT_FOUND"));
	}

	@Test
	void recommendedBundlesAreReadOnly() throws Exception {
		String recommended = recommendedBundle(OUTFIT, 0);
		customer.patchJson("/api/v1/bundles/" + recommended + "/items/men-jeans", "{\"locked\":true}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BUNDLE_READ_ONLY"));
	}

	@Test
	void alternativesShowPriceChangesAndPiecesCanBeSwapped() throws Exception {
		String draft = customize(recommendedBundle(OUTFIT, 1));
		String body = customer.get("/api/v1/bundles/" + draft + "/alternatives?category=men-tshirts")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].current").value(true))
			.andExpect(jsonPath("$[0].priceDelta").value(0.0))
			.andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> alternatives = JsonPath.read(body, "$");
		Map<String, Object> other = alternatives.stream()
			.filter(a -> !(Boolean) a.get("current") && (Boolean) a.get("inStock"))
			.findFirst()
			.orElseThrow();

		customer.putJson("/api/v1/bundles/" + draft + "/items/men-tshirts", "{\"productId\":" + other.get("productId") + "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'men-tshirts')].productId").value(other.get("productId")))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'men-tshirts')].explanation").value("Chosen by you."));
		long watch = jdbc.sql("""
				SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE c.slug = 'watches' LIMIT 1
				""").query(Long.class).single();
		customer.putJson("/api/v1/bundles/" + draft + "/items/men-tshirts", "{\"productId\":" + watch + "}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("WRONG_CATEGORY"));
	}

	@Test
	void quantitiesAndLocksUpdateTotals() throws Exception {
		String draft = customize(recommendedBundle(OUTFIT, 0));
		String before = customer.get("/api/v1/bundles/" + draft).andReturn().getResponse().getContentAsString();
		double total = ((Number) JsonPath.read(before, "$.totalPrice")).doubleValue();
		double teePrice = ((Number) JsonPath
			.<List<Number>>read(before, "$.items[?(@.categorySlug == 'men-tshirts')].currentPrice").getFirst()).doubleValue();

		customer.patchJson("/api/v1/bundles/" + draft + "/items/men-tshirts", "{\"quantity\":2,\"locked\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'men-tshirts')].quantity").value(2))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'men-tshirts')].locked").value(true))
			.andExpect(jsonPath("$.totalPrice").value(closeTo(total + teePrice, 0.001)));
		customer.patchJson("/api/v1/bundles/" + draft + "/items/men-tshirts", "{\"quantity\":11}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void accessoriesCanBeAddedAndOnlyOptionalPiecesRemoved() throws Exception {
		String draft = customize(recommendedBundle(OUTFIT, 1));
		String body = customer.get("/api/v1/bundles/" + draft + "/accessories")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].categorySlug", hasItem("sunglasses")))
			.andExpect(jsonPath("$[?(@.categorySlug == 'sunglasses')].reason", hasItem(startsWith("Popular for"))))
			.andExpect(jsonPath("$[*].products[*].compatible", everyItem(is(true))))
			.andReturn().getResponse().getContentAsString();
		Number shades = JsonPath.<List<Number>>read(body, "$[?(@.categorySlug == 'sunglasses')].products[0].productId").getFirst();

		customer.postJson("/api/v1/bundles/" + draft + "/items", "{\"productId\":" + shades + "}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(4))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'sunglasses')].optional").value(true));
		customer.postJson("/api/v1/bundles/" + draft + "/items", "{\"productId\":" + shades + "}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_IN_BUNDLE"));

		customer.delete("/api/v1/bundles/" + draft + "/items/men-tshirts")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REQUIRED_ITEM"));
		customer.delete("/api/v1/bundles/" + draft + "/items/sunglasses")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3));
	}

	@Test
	void reoptimizingKeepsLockedItemsAndRespectsTheNewBudget() throws Exception {
		String draft = customize(recommendedBundle(OUTFIT, 0));
		String before = customer.get("/api/v1/bundles/" + draft).andReturn().getResponse().getContentAsString();
		Number lockedJeans = JsonPath.<List<Number>>read(before, "$.items[?(@.categorySlug == 'men-jeans')].productId").getFirst();
		customer.patchJson("/api/v1/bundles/" + draft + "/items/men-jeans", "{\"locked\":true}").andExpect(status().isOk());

		customer.putJson("/api/v1/bundles/" + draft + "/budget", "{\"budget\":600}")
			.andExpect(jsonPath("$.budget").value(600.0));
		String after = customer.post("/api/v1/bundles/" + draft + "/reoptimize?strategy=premium")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<Number>>read(after, "$.items[?(@.categorySlug == 'men-jeans')].productId").getFirst().longValue())
			.isEqualTo(lockedJeans.longValue());
		assertThat(((Number) JsonPath.read(after, "$.totalPrice")).doubleValue()).isLessThanOrEqualTo(600.0)
			.isGreaterThan(((Number) JsonPath.read(before, "$.totalPrice")).doubleValue());

		customer.putJson("/api/v1/bundles/" + draft + "/budget", "{\"budget\":20}").andExpect(status().isOk());
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
		String draft = customize(recommendedBundle(OUTFIT, 2));
		customer.postJson("/api/v1/bundles/" + draft + "/save", "{\"name\":\"Saturday look\"}")
			.andExpect(jsonPath("$.status").value("SAVED"))
			.andExpect(jsonPath("$.name").value("Saturday look"))
			.andExpect(jsonPath("$.editable").value(true));
		customer.get("/api/v1/me/bundles")
			.andExpect(jsonPath("$[0].id").value(draft))
			.andExpect(jsonPath("$[0].status").value("SAVED"))
			.andExpect(jsonPath("$[0].goalTitle").value("Weekend casual (men)"));

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
