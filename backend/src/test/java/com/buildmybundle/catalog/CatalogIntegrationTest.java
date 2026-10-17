package com.buildmybundle.catalog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.Test;

/** Runs against the seeded demo catalog (db/seed), anonymously: catalog reads are public. */
class CatalogIntegrationTest extends IntegrationTest {

	private static final List<String> COMPUTER_CATEGORIES = List.of("monitors", "keyboards", "mice", "webcams",
			"desktops", "laptops", "cpus", "motherboards");

	@Test
	void categoryTreeListsDepartmentsWithTheirCategories() throws Exception {
		mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(6)))
			.andExpect(jsonPath("$[1].slug").value("computers"))
			.andExpect(jsonPath("$[1].children[*].slug", hasItems("monitors", "laptops", "cpus")))
			.andExpect(jsonPath("$[1].productCount").value(39));
	}

	@Test
	void fullTextSearchMatchesWordPrefixesAndStems() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("q", "stand desk"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].sku", hasItems("DSK-003", "DSK-004")));

		mockMvc.perform(get("/api/v1/products").param("q", "monit"))
			.andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(7)))
			.andExpect(jsonPath("$.content[*].sku", hasItem("MON-001")));
	}

	@Test
	void departmentScopeIncludesAllChildCategoriesAndPaginates() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("category", "computers").param("size", "10").param("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(39))
			.andExpect(jsonPath("$.totalPages").value(4))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.content", hasSize(10)))
			.andExpect(jsonPath("$.content[*].categorySlug", everyItem(in(COMPUTER_CATEGORIES))));
	}

	@Test
	void filtersByBrandPriceAndAttributes() throws Exception {
		mockMvc
			.perform(get("/api/v1/products").param("category", "monitors").param("brand", "Pixelon")
				.param("maxPrice", "400"))
			.andExpect(jsonPath("$.content[*].sku", contains("MON-004", "MON-005")))
			.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(get("/api/v1/products").param("category", "monitors").param("attr.inputs", "USB-C"))
			.andExpect(jsonPath("$.totalElements").value(3));

		mockMvc
			.perform(get("/api/v1/products").param("category", "monitors").param("attr.inputs", "USB-C")
				.param("attr.panel", "OLED"))
			.andExpect(jsonPath("$.content[*].sku", contains("MON-006")));

		// numeric attributes use their canonical text form
		mockMvc.perform(get("/api/v1/products").param("category", "monitors").param("attr.weight_kg", "3.2"))
			.andExpect(jsonPath("$.content[*].sku", contains("MON-001")));
	}

	@Test
	void sortsByPrice() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("category", "mice").param("sort", "price_asc"))
			.andExpect(jsonPath("$.content[0].sku").value("MSE-001"))
			.andExpect(jsonPath("$.content[4].sku").value("MSE-005"));
		mockMvc.perform(get("/api/v1/products").param("category", "mice").param("sort", "price_desc"))
			.andExpect(jsonPath("$.content[0].sku").value("MSE-005"));
	}

	@Test
	void rejectsInvalidQueryParameters() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("sort", "cheapest"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SORT"));
		mockMvc.perform(get("/api/v1/products").param("minPrice", "500").param("maxPrice", "100"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PRICE_RANGE"));
		mockMvc.perform(get("/api/v1/products").param("attr.bad key", "x"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ATTRIBUTE_FILTER"));
	}

	@Test
	void facetsDescribeBrandsPriceRangeAndAttributeValues() throws Exception {
		mockMvc.perform(get("/api/v1/products/facets").param("category", "monitors"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.brands[?(@.value == 'Vantix')].count").value(3))
			.andExpect(jsonPath("$.minPrice").value(129.00))
			.andExpect(jsonPath("$.maxPrice").value(899.00))
			.andExpect(jsonPath("$.attributes[?(@.key == 'inputs')].values[?(@.value == 'USB-C')].count").value(3))
			.andExpect(jsonPath("$.attributes[*].key", hasItems("refresh_hz", "panel")));

		// attribute facets only make sense within a single leaf category
		mockMvc.perform(get("/api/v1/products/facets").param("category", "computers"))
			.andExpect(jsonPath("$.attributes", hasSize(0)));
	}

	@Test
	void productDetailIncludesSpecsStockBreadcrumbAndRelatedProducts() throws Exception {
		mockMvc.perform(get("/api/v1/products/vantix-27-4k-usb-c-monitor"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.product.sku").value("MON-003"))
			.andExpect(jsonPath("$.product.price").value(449.00))
			.andExpect(jsonPath("$.product.availableQuantity", greaterThan(0)))
			.andExpect(jsonPath("$.department.slug").value("computers"))
			.andExpect(jsonPath("$.attributes[?(@.key == 'inputs')].values[*]", contains("HDMI", "DisplayPort", "USB-C")))
			.andExpect(jsonPath("$.tags", hasItem("photography")))
			.andExpect(jsonPath("$.related", hasSize(4)))
			.andExpect(jsonPath("$.related[*].sku", not(hasItem("MON-003"))));
	}

	@Test
	void outOfStockProductsAreListedWithZeroAvailability() throws Exception {
		mockMvc.perform(get("/api/v1/products/corvex-gaming-tower-4k-ultra"))
			.andExpect(jsonPath("$.product.availableQuantity").value(0));
	}

	@Test
	void unknownProductReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/products/does-not-exist"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}
}
