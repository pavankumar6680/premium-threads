package com.buildmybundle.admin;

import static com.buildmybundle.support.BrowserSession.longField;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class AdminCatalogIntegrationTest extends IntegrationTest {

	private BrowserSession admin;
	private String category;

	@BeforeEach
	void setUp() throws Exception {
		admin = newAdmin();
		category = "gadgets-" + UUID.randomUUID().toString().substring(0, 8);
		admin.postJson("/api/v1/admin/categories",
				"{\"departmentSlug\":\"home-office\",\"slug\":\"" + category + "\",\"name\":\"Test gadgets\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.leaf").value(true));
		admin.postJson("/api/v1/admin/categories/" + category + "/attributes",
				"{\"key\":\"ports\",\"label\":\"Ports\",\"type\":\"LIST\"}").andExpect(status().isCreated());
		admin.postJson("/api/v1/admin/categories/" + category + "/attributes",
				"{\"key\":\"weight_kg\",\"label\":\"Weight\",\"type\":\"NUMBER\",\"unit\":\"kg\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.attributes.length()").value(2));
	}

	@Test
	void createdProductsAreSellableWithTheirAttributes() throws Exception {
		String sku = "GAD-" + UUID.randomUUID().toString().substring(0, 6);
		ResultActions created = create(sku, "Travel Dock", "79.90", 12,
				"{\"ports\":[\"USB-C\",\"HDMI\"],\"weight_kg\":[\"0.25\"]}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sku").value(sku.toUpperCase()))
			.andExpect(jsonPath("$.slug").value("travel-dock"))
			.andExpect(jsonPath("$.stock").value(12))
			.andExpect(jsonPath("$.attributes.ports[1]").value("HDMI"))
			.andExpect(jsonPath("$.version").value(0));
		long id = longField(created, "$.id");

		// the storefront sees it immediately, with stock and specifications
		new BrowserSession(mockMvc).get("/api/v1/products/travel-dock")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.product.id").value(id))
			.andExpect(jsonPath("$.product.availableQuantity").value(12))
			.andExpect(jsonPath("$.attributes[?(@.key == 'weight_kg')].values[0]").value("0.25"));
		admin.get("/api/v1/admin/audit-log?entityType=PRODUCT")
			.andExpect(jsonPath("$.content[0].action").value("PRODUCT_CREATED"))
			.andExpect(jsonPath("$.content[0].entityId").value(String.valueOf(id)));
	}

	@Test
	void attributesAreValidatedAgainstTheCategory() throws Exception {
		create("GAD-X1", "Gizmo", "10.00", 1, "{\"colour\":[\"red\"]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_ATTRIBUTE"))
			.andExpect(jsonPath("$.attribute").value("colour"));
		create("GAD-X2", "Gizmo", "10.00", 1, "{\"weight_kg\":[\"heavy\"]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ATTRIBUTE"));
		create("GAD-X3", "Gizmo", "10.00", 1, "{\"weight_kg\":[\"1\",\"2\"]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ATTRIBUTE"));
		create("GAD-X4", "Gizmo", "-1", 1, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
		admin.postJson("/api/v1/admin/products", productJson("home-office", "GAD-X5", "Gizmo", "10.00", 1, "{}", null))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY")); // a department, not a category
	}

	@Test
	void skusAreUnique() throws Exception {
		String sku = "GAD-" + UUID.randomUUID().toString().substring(0, 6);
		create(sku, "First", "10.00", 1, "{}").andExpect(status().isCreated());
		create(sku.toLowerCase(), "Second", "10.00", 1, "{}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SKU_TAKEN"));
	}

	@Test
	void concurrentEditsAreDetected() throws Exception {
		String sku = "GAD-" + UUID.randomUUID().toString().substring(0, 6);
		long id = longField(create(sku, "Desk Hub", "50.00", 3, "{}"), "$.id");

		admin.putJson("/api/v1/admin/products/" + id, productJson(category, sku, "Desk Hub", "45.00", null, "{}", 0L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.price").value(45.0))
			.andExpect(jsonPath("$.version").value(1));
		// a second admin still editing version 0 must not overwrite the new price
		admin.putJson("/api/v1/admin/products/" + id, productJson(category, sku, "Desk Hub", "60.00", null, "{}", 0L))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_PRODUCT"))
			.andExpect(jsonPath("$.currentVersion").value(1));
		admin.get("/api/v1/admin/products/" + id).andExpect(jsonPath("$.price").value(45.0));
		admin.get("/api/v1/admin/audit-log?entityType=PRODUCT")
			.andExpect(jsonPath("$.content[0].action").value("PRODUCT_UPDATED"))
			.andExpect(jsonPath("$.content[0].details").value(org.hamcrest.Matchers.containsString("\"price\"")));
	}

	@Test
	void stockAndStatusChangesReachTheStorefront() throws Exception {
		String sku = "GAD-" + UUID.randomUUID().toString().substring(0, 6);
		long id = longField(create(sku, "Cable Kit " + sku, "15.00", 20, "{}"), "$.id");
		String slug = admin.get("/api/v1/admin/products/" + id).andReturn().getResponse().getContentAsString()
			.replaceAll(".*\"slug\":\"([^\"]+)\".*", "$1");

		admin.putJson("/api/v1/admin/products/" + id + "/stock", "{\"quantity\":2}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stock").value(2));
		admin.get("/api/v1/admin/products?lowStock=true&category=" + category)
			.andExpect(jsonPath("$.content[*].id", hasItem((int) id)));
		admin.get("/api/v1/admin/products?q=" + sku).andExpect(jsonPath("$.totalElements").value(1));

		admin.patchJson("/api/v1/admin/products/" + id + "/status", "{\"active\":false}")
			.andExpect(jsonPath("$.active").value(false));
		new BrowserSession(mockMvc).get("/api/v1/products/" + slug).andExpect(status().isNotFound());
		admin.get("/api/v1/admin/products?active=true&category=" + category)
			.andExpect(jsonPath("$.content[*].id", not(hasItem((int) id))));

		admin.putJson("/api/v1/admin/products/" + id + "/stock", "{\"quantity\":-1}").andExpect(status().isBadRequest());
		admin.putJson("/api/v1/admin/products/999999/stock", "{\"quantity\":1}").andExpect(status().isNotFound());
	}

	@Test
	void categoriesAndAttributesAreValidated() throws Exception {
		admin.postJson("/api/v1/admin/categories", "{\"departmentSlug\":\"home-office\",\"slug\":\"" + category + "\",\"name\":\"Again\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_SLUG_TAKEN"));
		admin.postJson("/api/v1/admin/categories", "{\"departmentSlug\":\"nowhere\",\"slug\":\"x-new\",\"name\":\"X\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_DEPARTMENT"));
		admin.postJson("/api/v1/admin/categories/" + category + "/attributes", "{\"key\":\"ports\",\"label\":\"Ports\",\"type\":\"LIST\"}")
			.andExpect(status().isConflict());
		admin.postJson("/api/v1/admin/categories/" + category + "/attributes", "{\"key\":\"Bad Key\",\"label\":\"X\",\"type\":\"TEXT\"}")
			.andExpect(status().isBadRequest());
	}

	private ResultActions create(String sku, String name, String price, Integer stock, String attributes) throws Exception {
		return admin.postJson("/api/v1/admin/products", productJson(category, sku, name, price, stock, attributes, null));
	}

	private static String productJson(String category, String sku, String name, String price, Integer stock,
			String attributes, Long version) {
		return """
				{"categorySlug":"%s","sku":"%s","name":"%s","brand":"Testbrand","description":"Made by a test",
				 "price":%s,"qualityTier":2,"active":true,"tags":["travel"],"attributes":%s,"stock":%s,"version":%s}
				""".formatted(category, sku, name, price, attributes, stock, version);
	}
}
