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
				"{\"departmentSlug\":\"accessories\",\"slug\":\"" + category + "\",\"name\":\"Test gadgets\"}")
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
		admin.postJson("/api/v1/admin/products", productJson("accessories", "GAD-X5", "Gizmo", "10.00", 1, "{}", null))
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
		admin.postJson("/api/v1/admin/categories", "{\"departmentSlug\":\"accessories\",\"slug\":\"" + category + "\",\"name\":\"Again\"}")
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
				{"categorySlug":"%s","sku":"%s","name":"%s","brand":"Uniqlo","description":"Made by a test",
				 "price":%s,"qualityTier":2,"active":true,"tags":["travel"],"attributes":%s,"stock":%s,"version":%s}
				""".formatted(category, sku, name, price, attributes, stock, version);
	}

	@Test
	void productsAreSoldInSizeAndColorVariantsWithTheirOwnStock() throws Exception {
		String sku = "FSH-" + UUID.randomUUID().toString().substring(0, 6);
		String body = admin.postJson("/api/v1/admin/products", """
				{"categorySlug":"women-tops","sku":"%s","name":"Variant Blouse %s","brand":"mango","price":49.99,
				 "compareAtPrice":69.99,"qualityTier":2,"active":true,"gender":"WOMEN","newArrival":true,
				 "material":"100%% viscose","care":"Hand wash cold.","styles":["formal","minimalist"],"occasions":["office"],
				 "variants":[{"size":"S","color":"Cream","colorHex":"#F3EAD7","stock":4},
				             {"size":"M","color":"Cream","colorHex":"#F3EAD7","stock":0},
				             {"size":"M","color":"Black","colorHex":"#1F1F1F","colorFamily":"NEUTRAL","stock":7}]}
				""".formatted(sku, sku))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.brand").value("Mango"))
			.andExpect(jsonPath("$.gender").value("WOMEN"))
			.andExpect(jsonPath("$.compareAtPrice").value(69.99))
			.andExpect(jsonPath("$.styles", org.hamcrest.Matchers.contains("formal", "minimalist")))
			.andExpect(jsonPath("$.variants.length()").value(3))
			.andExpect(jsonPath("$.stock").value(11))
			.andReturn().getResponse().getContentAsString();
		long id = com.jayway.jsonpath.JsonPath.<Number>read(body, "$.id").longValue();
		long version = com.jayway.jsonpath.JsonPath.<Number>read(body, "$.version").longValue();
		long cream = com.jayway.jsonpath.JsonPath.<Number>read(body, "$.variants[0].id").longValue();
		long black = com.jayway.jsonpath.JsonPath.<Number>read(body, "$.variants[2].id").longValue();
		String slug = com.jayway.jsonpath.JsonPath.read(body, "$.slug");

		// the storefront shows the options, the sale price and stock per size
		new BrowserSession(mockMvc).get("/api/v1/products/" + slug)
			.andExpect(jsonPath("$.product.compareAtPrice").value(69.99))
			.andExpect(jsonPath("$.product.newArrival").value(true))
			.andExpect(jsonPath("$.product.inStockSizes", org.hamcrest.Matchers.contains("S", "M")))
			.andExpect(jsonPath("$.variants[?(@.size == 'M' && @.color == 'Cream')].availableQuantity").value(0));

		// several options: stock is set per variant
		admin.putJson("/api/v1/admin/products/" + id + "/stock", "{\"quantity\":5}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VARIANT_REQUIRED"));
		admin.putJson("/api/v1/admin/variants/" + black + "/stock", "{\"quantity\":2}")
			.andExpect(jsonPath("$.stock").value(6));

		// dropping a variant from the list deactivates it; unspecified merchandising fields are kept
		admin.putJson("/api/v1/admin/products/" + id, """
				{"categorySlug":"women-tops","sku":"%s","name":"Variant Blouse %s","brand":"Mango","price":49.99,
				 "qualityTier":2,"active":true,"version":%d,
				 "variants":[{"id":%d,"size":"S","color":"Cream","colorHex":"#F3EAD7"},
				             {"size":"L","color":"Cream","colorHex":"#F3EAD7","stock":3}]}
				""".formatted(sku, sku, version, cream))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.gender").value("WOMEN"))
			.andExpect(jsonPath("$.compareAtPrice").value(69.99))
			.andExpect(jsonPath("$.variants[?(@.id == " + black + ")].active").value(false))
			.andExpect(jsonPath("$.variants[?(@.size == 'L')].stock").value(3))
			.andExpect(jsonPath("$.stock").value(7)); // S 4 + L 3; inactive variants do not count

		// validation: a discount must be a real reduction, options must be distinct, vocabularies must exist
		admin.postJson("/api/v1/admin/products", """
				{"categorySlug":"women-tops","sku":"%s-B","name":"Bad discount","brand":"Mango","price":50,"compareAtPrice":40,
				 "qualityTier":2,"active":true}""".formatted(sku)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DISCOUNT"));
		admin.postJson("/api/v1/admin/products", """
				{"categorySlug":"women-tops","sku":"%s-C","name":"Twice","brand":"Mango","price":50,"qualityTier":2,"active":true,
				 "variants":[{"size":"S","color":"Red"},{"size":"s","color":"red"}]}""".formatted(sku)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DUPLICATE_VARIANT"));
		admin.postJson("/api/v1/admin/products", """
				{"categorySlug":"women-tops","sku":"%s-D","name":"Odd style","brand":"Mango","price":50,"qualityTier":2,"active":true,
				 "styles":["gothic"]}""".formatted(sku)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_STYLE"));
		admin.postJson("/api/v1/admin/products", """
				{"categorySlug":"women-tops","sku":"%s-E","name":"No brand","brand":"Acme","price":50,"qualityTier":2,"active":true}
				""".formatted(sku)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_BRAND"));
	}

	@Test
	void brandsAreManagedAsData() throws Exception {
		String name = "Demo Label " + UUID.randomUUID().toString().substring(0, 5);
		String body = admin.postJson("/api/v1/admin/brands", "{\"name\":\"" + name + "\",\"description\":\"A demo label\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value(name.toLowerCase().replace(' ', '-')))
			.andReturn().getResponse().getContentAsString();
		long brandId = com.jayway.jsonpath.JsonPath.<Number>read(body, "$.id").longValue();
		admin.postJson("/api/v1/admin/brands", "{\"name\":\"" + name.toUpperCase() + "\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BRAND_NAME_TAKEN"));

		// a product of the new brand; renaming the brand renames it on the product
		String sku = "DLB-" + UUID.randomUUID().toString().substring(0, 6);
		admin.postJson("/api/v1/admin/products", """
				{"categorySlug":"scarves","sku":"%s","name":"Demo Scarf %s","brand":"%s","price":20,"qualityTier":1,
				 "active":true,"stock":3}""".formatted(sku, sku, name)).andExpect(status().isCreated());
		admin.putJson("/api/v1/admin/brands/" + brandId, "{\"name\":\"" + name + " Studio\",\"featured\":true}")
			.andExpect(jsonPath("$.name").value(name + " Studio"))
			.andExpect(jsonPath("$.featured").value(true))
			.andExpect(jsonPath("$.productCount").value(1));
		new BrowserSession(mockMvc).get("/api/v1/products?q=" + sku)
			.andExpect(jsonPath("$.content[0].brand").value(name + " Studio"));
		new BrowserSession(mockMvc).get("/api/v1/brands")
			.andExpect(jsonPath("$[*].name", hasItem(name + " Studio")));
	}
}
