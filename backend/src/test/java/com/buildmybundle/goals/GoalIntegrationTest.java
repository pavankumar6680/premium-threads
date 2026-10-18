package com.buildmybundle.goals;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

/** Runs against the seeded fashion catalog and the Build Your Outfit templates. */
class GoalIntegrationTest extends IntegrationTest {

	// cheapest in-stock essentials of the men's weekend look: H&M tee 13.00 + H&M slim jeans 29.99
	private static final double WEEKEND_MEN_ESSENTIALS = 42.99;

	@Test
	void listsOutfitTemplatesForEveryOccasionAndWardrobe() throws Exception {
		mockMvc.perform(get("/api/v1/goal-templates"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(16)))
			.andExpect(jsonPath("$[*].slug", hasItems("weekend-casual-men", "weekend-casual-women", "office-men",
					"office-women", "party-women", "festive-men", "festive-women", "streetwear-men", "workout-women")))
			.andExpect(jsonPath("$[0].slug").value("weekend-casual-men"))
			.andExpect(jsonPath("$[0].essentialsMinimum").value(WEEKEND_MEN_ESSENTIALS))
			.andExpect(jsonPath("$[0].categoryNames", hasItem("Sunglasses")));
	}

	@Test
	void templateDetailIncludesLivePriceRangesAndStyleOptions() throws Exception {
		mockMvc.perform(get("/api/v1/goal-templates/weekend-casual-men"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].category.slug").value("men-tshirts"))
			.andExpect(jsonPath("$.items[0].required").value(true))
			.andExpect(jsonPath("$.items[0].category.minPrice").value(13.00))
			.andExpect(jsonPath("$.items[0].category.brands", hasItems("Nike", "Uniqlo", "Lacoste")))
			.andExpect(jsonPath("$.usageOptions[*].tag", hasItems("casual", "streetwear", "minimalist", "formal", "party-wear")));

		mockMvc.perform(get("/api/v1/goal-templates/home-office"))
			.andExpect(status().isNotFound()) // retired with the electronics catalog
			.andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
	}

	@Test
	void outOfStockProductsAreExcludedFromPriceRanges() throws Exception {
		jdbc.sql("""
				WITH p AS (
				    INSERT INTO products (category_id, brand_id, brand, sku, slug, name, price, gender)
				    SELECT c.id, b.id, b.name, 'SOLDOUT-TEE', 'soldout-tee', 'Sold Out Tee', 1.00, 'MEN'
				    FROM categories c, brands b WHERE c.slug = 'men-tshirts' AND b.slug = 'uniqlo'
				    RETURNING id)
				INSERT INTO product_variants (product_id, sku, size, color) SELECT id, 'SOLDOUT-TEE-M', 'M', 'White' FROM p
				""").update();
		mockMvc.perform(get("/api/v1/goal-templates/weekend-casual-men"))
			.andExpect(jsonPath("$.items[0].category.minPrice").value(13.00));
	}

	@Test
	void listsEveryFashionCategoryForCustomOutfits() throws Exception {
		mockMvc.perform(get("/api/v1/goal-categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].slug", hasItems("women-dresses", "men-ethnic-wear", "scarves")))
			.andExpect(jsonPath("$[*].slug", not(hasItem("monitors"))))
			.andExpect(jsonPath("$[?(@.slug == 'women')]").isEmpty()); // departments are not purchasable
	}

	@Test
	void readsOccasionWardrobeAndBudgetFromFreeText() throws Exception {
		new BrowserSession(mockMvc)
			.postJson("/api/v1/goals/analyze", """
					{"text":"I need a party outfit for her: a dress, a small bag and some jewelry, under $250."}
					""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.templateSlug").value("party-women"))
			.andExpect(jsonPath("$.budget").value(250.00))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'women-dresses')].kind").value("REQUIRED"))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'jewelry')].kind").value("REQUIRED"))
			.andExpect(jsonPath("$.source").value("RULES"));
		// ambiguous garment words follow the wardrobe of the matched template
		new BrowserSession(mockMvc)
			.postJson("/api/v1/goals/analyze", "{\"text\":\"Office clothes for men: a shirt, trousers and a belt, about 300 dollars\"}")
			.andExpect(jsonPath("$.templateSlug").value("office-men"))
			.andExpect(jsonPath("$.items[*].categorySlug", hasItems("men-shirts", "men-trousers", "belts")))
			.andExpect(jsonPath("$.items[*].categorySlug", everyItem(not(startsWith("women-")))));
	}

	@Test
	void anonymousVisitorsCanCreateAndShareAnOutfitGoal() throws Exception {
		BrowserSession anonymous = new BrowserSession(mockMvc);
		String body = anonymous.postJson("/api/v1/goals", """
				{"templateSlug":"weekend-casual-men","budget":200,"quality":"BALANCED",
				 "items":[{"categorySlug":"men-tshirts","kind":"REQUIRED"},{"categorySlug":"men-jeans","kind":"OWNED"},
				          {"categorySlug":"men-jackets","kind":"OPTIONAL"},{"categorySlug":"sunglasses","kind":"OPTIONAL"}],
				 "preferredBrands":["levi's"],"usageTags":["casual"]}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id", matchesPattern("[0-9a-f-]{36}")))
			.andExpect(jsonPath("$.title").value("Weekend casual (men)"))
			.andExpect(jsonPath("$.items", hasSize(4)))
			.andExpect(jsonPath("$.items[1].kind").value("OWNED"))
			.andExpect(jsonPath("$.preferredBrands[0]").value("Levi's")) // canonical spelling
			// the jeans are owned, so only the cheapest tee counts as essential
			.andExpect(jsonPath("$.estimate.essentialsMinimum").value(13.00))
			.andExpect(jsonPath("$.estimate.essentialsFit").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mockMvc.perform(get("/api/v1/goals/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
		mockMvc.perform(get("/api/v1/me/goals")).andExpect(status().isUnauthorized());
	}

	@Test
	void signedInCustomersSeeTheirGoals() throws Exception {
		BrowserSession customer = newCustomer();
		customer.postJson("/api/v1/goals", """
				{"title":"Wedding guest","budget":400,"quality":"PREMIUM",
				 "items":[{"categorySlug":"women-ethnic-wear","kind":"REQUIRED","quantity":1},{"categorySlug":"jewelry","kind":"OPTIONAL"}]}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.template").isEmpty());

		customer.get("/api/v1/me/goals")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Wedding guest"))
			.andExpect(jsonPath("$.content[0].itemCount").value(2));
	}

	@Test
	void flagsBudgetsBelowTheCheapestPossibleOutfit() throws Exception {
		new BrowserSession(mockMvc).postJson("/api/v1/goals", """
				{"templateSlug":"weekend-casual-men","budget":20,"quality":"BUDGET",
				 "items":[{"categorySlug":"men-tshirts","kind":"REQUIRED"},{"categorySlug":"men-jeans","kind":"REQUIRED"}]}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estimate.essentialsMinimum").value(WEEKEND_MEN_ESSENTIALS))
			.andExpect(jsonPath("$.estimate.essentialsFit").value(false));
	}

	@Test
	void rejectsInvalidGoals() throws Exception {
		BrowserSession browser = new BrowserSession(mockMvc);
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"monitors\",\"kind\":\"REQUIRED\"}]", "[]"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY")); // retired category
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"women\",\"kind\":\"REQUIRED\"}]", "[]"))
			.andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY")); // departments are not purchasable categories
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"women-dresses\",\"kind\":\"OWNED\"}]", "[]"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NOTHING_TO_BUY"));
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"watches\",\"kind\":\"REQUIRED\"},{\"categorySlug\":\"watches\",\"kind\":\"OPTIONAL\"}]", "[]"))
			.andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY"));
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"watches\",\"kind\":\"REQUIRED\"}]", "[\"Acme\"]"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_BRAND"));
		browser.postJson("/api/v1/goals", """
				{"budget":0,"quality":"BALANCED","items":[]}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("budget")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("items")));
	}

	@Test
	void unknownGoalReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/goals/00000000-0000-0000-0000-000000000000"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
	}

	private static String goal(String items, String brands) {
		return "{\"budget\":500,\"quality\":\"BALANCED\",\"items\":" + items + ",\"preferredBrands\":" + brands + "}";
	}
}
