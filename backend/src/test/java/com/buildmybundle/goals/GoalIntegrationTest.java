package com.buildmybundle.goals;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

/** Runs against the seeded catalog and goal templates. */
class GoalIntegrationTest extends IntegrationTest {

	// cheapest in-stock essentials of the home office template: desk 129 + chair 99 + monitor 129 + keyboard 39 + mouse 19
	private static final double HOME_OFFICE_ESSENTIALS = 415.00;

	@Test
	void listsTemplatesWithTheCheapestCostOfTheirEssentials() throws Exception {
		mockMvc.perform(get("/api/v1/goal-templates"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(7)))
			.andExpect(jsonPath("$[0].slug").value("home-office"))
			.andExpect(jsonPath("$[0].essentialsMinimum").value(HOME_OFFICE_ESSENTIALS))
			.andExpect(jsonPath("$[0].categoryNames", hasItem("Desk Lamps")));
	}

	@Test
	void templateDetailIncludesLivePriceRangesAndUsageOptions() throws Exception {
		mockMvc.perform(get("/api/v1/goal-templates/home-office"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].category.slug").value("desks"))
			.andExpect(jsonPath("$.items[0].required").value(true))
			.andExpect(jsonPath("$.items[0].category.minPrice").value(129.00))
			.andExpect(jsonPath("$.items[0].category.maxPrice").value(649.00))
			.andExpect(jsonPath("$.items[0].category.brands", hasItem("Ergolift")))
			.andExpect(jsonPath("$.usageOptions[*].tag", hasItem("ergonomic")));

		mockMvc.perform(get("/api/v1/goal-templates/space-station"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
	}

	@Test
	void outOfStockProductsAreExcludedFromPriceRanges() throws Exception {
		// the 4K Ultra tower (2799) has no stock, so the desktop range tops out at the 1440p tower
		mockMvc.perform(get("/api/v1/goal-templates/gaming-setup"))
			.andExpect(jsonPath("$.items[0].category.slug").value("desktops"))
			.andExpect(jsonPath("$.items[0].category.maxPrice").value(1599.00))
			.andExpect(jsonPath("$.items[0].category.inStockCount").value(4));
	}

	@Test
	void listsEveryLeafCategoryForCustomGoals() throws Exception {
		mockMvc.perform(get("/api/v1/goal-categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].slug", hasItem("video-doorbells")))
			.andExpect(jsonPath("$[?(@.slug == 'computers')]").isEmpty());
	}

	@Test
	void analyzesTheReferenceRequestAnonymously() throws Exception {
		new BrowserSession(mockMvc)
			.postJson("/api/v1/goals/analyze", """
					{"text":"I want to set up a professional work-from-home office with a budget of $1,200. I need a desk, ergonomic chair, monitor, keyboard, mouse, and lighting."}
					""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.templateSlug").value("home-office"))
			.andExpect(jsonPath("$.budget").value(1200.00))
			.andExpect(jsonPath("$.items[?(@.categorySlug == 'desk-lamps')].kind").value("REQUIRED"))
			.andExpect(jsonPath("$.usageTags", hasItem("ergonomic")))
			.andExpect(jsonPath("$.source").value("RULES"));
	}

	@Test
	void anonymousVisitorsCanCreateAndShareAGoal() throws Exception {
		BrowserSession anonymous = new BrowserSession(mockMvc);
		String body = anonymous.postJson("/api/v1/goals", """
				{"templateSlug":"home-office","budget":1200,"quality":"BALANCED",
				 "items":[{"categorySlug":"desks","kind":"REQUIRED"},{"categorySlug":"chairs","kind":"REQUIRED"},
				          {"categorySlug":"monitors","kind":"OWNED"},{"categorySlug":"keyboards","kind":"REQUIRED"},
				          {"categorySlug":"mice","kind":"REQUIRED"},{"categorySlug":"desk-lamps","kind":"OPTIONAL"}],
				 "preferredBrands":["ergolift"],"usageTags":["ergonomic"],"space":{"widthCm":140,"depthCm":70}}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id", matchesPattern("[0-9a-f-]{36}")))
			.andExpect(jsonPath("$.title").value("Professional home office"))
			.andExpect(jsonPath("$.template.slug").value("home-office"))
			.andExpect(jsonPath("$.items", hasSize(6)))
			.andExpect(jsonPath("$.items[2].kind").value("OWNED"))
			.andExpect(jsonPath("$.preferredBrands[0]").value("Ergolift")) // canonical spelling
			.andExpect(jsonPath("$.space.widthCm").value(140))
			// the monitor is owned, so it is not part of the cost: 415 - 129
			.andExpect(jsonPath("$.estimate.essentialsMinimum").value(286.00))
			// plus the cheapest lamp (29)
			.andExpect(jsonPath("$.estimate.selectedMinimum").value(315.00))
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
				{"title":"Garage gym","budget":500,"quality":"BUDGET",
				 "items":[{"categorySlug":"dumbbells","kind":"REQUIRED","quantity":1},{"categorySlug":"benches","kind":"OPTIONAL"}]}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.template").isEmpty());

		customer.get("/api/v1/me/goals")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Garage gym"))
			.andExpect(jsonPath("$.content[0].itemCount").value(2));
	}

	@Test
	void flagsBudgetsBelowTheCheapestPossibleSetup() throws Exception {
		new BrowserSession(mockMvc).postJson("/api/v1/goals", """
				{"templateSlug":"home-office","budget":100,"quality":"BUDGET",
				 "items":[{"categorySlug":"desks","kind":"REQUIRED"},{"categorySlug":"chairs","kind":"REQUIRED"}]}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estimate.essentialsMinimum").value(228.00))
			.andExpect(jsonPath("$.estimate.essentialsFit").value(false));
	}

	@Test
	void rejectsInvalidGoals() throws Exception {
		BrowserSession browser = new BrowserSession(mockMvc);
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"hoverboards\",\"kind\":\"REQUIRED\"}]", "[]"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY"));
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"computers\",\"kind\":\"REQUIRED\"}]", "[]"))
			.andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY")); // departments are not purchasable categories
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"desks\",\"kind\":\"OWNED\"}]", "[]"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NOTHING_TO_BUY"));
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"desks\",\"kind\":\"REQUIRED\"},{\"categorySlug\":\"desks\",\"kind\":\"OPTIONAL\"}]", "[]"))
			.andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY"));
		browser.postJson("/api/v1/goals", goal("[{\"categorySlug\":\"desks\",\"kind\":\"REQUIRED\"}]", "[\"Acme\"]"))
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
