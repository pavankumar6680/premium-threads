package com.buildmybundle.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.buildmybundle.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

/** Runs against the seeded fashion catalog (db/seed), anonymously: catalog reads are public. */
class CatalogIntegrationTest extends IntegrationTest {

	@Test
	void theStoreSellsOnlyMenWomenAndAccessories() throws Exception {
		mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].slug", containsInAnyOrder("men", "women", "accessories")))
			.andExpect(jsonPath("$[?(@.slug == 'men')].children[*].slug", hasItems("men-tshirts", "men-shirts",
					"men-jeans", "men-trousers", "men-jackets", "men-hoodies", "men-sweaters", "men-ethnic-wear")))
			.andExpect(jsonPath("$[?(@.slug == 'women')].children[*].slug", hasItems("women-dresses", "women-tops",
					"women-jeans", "women-trousers", "women-skirts", "women-jackets", "women-hoodies", "women-ethnic-wear")))
			.andExpect(jsonPath("$[?(@.slug == 'accessories')].children[*].slug", hasItems("watches", "sunglasses",
					"handbags", "backpacks", "wallets", "belts", "jewelry", "caps", "scarves")));
		// the previous electronics catalog is retired: not listed, not searchable
		mockMvc.perform(get("/api/v1/products").param("q", "monitor")).andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/products").param("category", "monitors"))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void atLeastFifteenBrandsEachWithSeveralProducts() throws Exception {
		String json = mockMvc.perform(get("/api/v1/brands")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> brands = JsonPath.read(json, "$");
		assertThat(brands).hasSizeGreaterThanOrEqualTo(15);
		assertThat(brands).extracting(b -> b.get("name")).contains("Nike", "Adidas", "Puma", "Zara", "H&M", "Levi's",
				"Tommy Hilfiger", "Calvin Klein", "Ralph Lauren", "Lacoste", "Uniqlo", "Guess", "Mango", "Under Armour",
				"Michael Kors");
		List<String> seeded = List.of("Nike", "Adidas", "Puma", "Zara", "H&M", "Levi's", "Tommy Hilfiger", "Calvin Klein",
				"Ralph Lauren", "Lacoste", "Uniqlo", "Guess", "Mango", "Under Armour", "Michael Kors");
		assertThat(brands).filteredOn(b -> seeded.contains(b.get("name")))
			.hasSize(15)
			.allSatisfy(b -> assertThat(((Number) b.get("productCount")).intValue()).isGreaterThanOrEqualTo(5));

		mockMvc.perform(get("/api/v1/brands/levi-s"))
			.andExpect(jsonPath("$.name").value("Levi's"))
			.andExpect(jsonPath("$.categories", hasItems("men", "women")));
		mockMvc.perform(get("/api/v1/brands/acme")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));
		// a brand page lists its products, by slug or by name
		mockMvc.perform(get("/api/v1/products").param("brand", "levi-s"))
			.andExpect(jsonPath("$.content[*].brand", everyItem(is("Levi's"))))
			.andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(5)));
		mockMvc.perform(get("/api/v1/products").param("brand", "Levi's"))
			.andExpect(jsonPath("$.content[*].brand", everyItem(is("Levi's"))));
	}

	@Test
	void productCardsCarryMerchandisingData() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("q", "levi 511"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].name").value("Levi's 511 Slim Jeans"))
			.andExpect(jsonPath("$.content[0].brandSlug").value("levi-s"))
			.andExpect(jsonPath("$.content[0].gender").value("MEN"))
			.andExpect(jsonPath("$.content[0].colors[*].name", hasItems("Dark Wash", "Mid Wash")))
			.andExpect(jsonPath("$.content[0].colors[0].hex").value("#26324F"))
			.andExpect(jsonPath("$.content[0].sizes", contains("28", "30", "32", "34", "36", "38")))
			.andExpect(jsonPath("$.content[0].availableQuantity", greaterThan(0)));
	}

	@Test
	void filtersBySizeInStockAndByColor() throws Exception {
		long tee = createProduct("Filtertee", "20.00", 0);
		deactivateDefaultVariant(tee);
		addVariant(tee, "M", "Navy", 3);
		addVariant(tee, "L", "Navy", 0); // L exists but is sold out
		addVariant(tee, "L", "Coral", 0);

		mockMvc.perform(get("/api/v1/products").param("q", "Filtertee").param("sizes", "M"))
			.andExpect(jsonPath("$.content[*].id", hasItem((int) tee)))
			.andExpect(jsonPath("$.content[0].inStockSizes", contains("M")))
			.andExpect(jsonPath("$.content[0].sizes", contains("M", "L")));
		mockMvc.perform(get("/api/v1/products").param("q", "Filtertee").param("sizes", "L"))
			.andExpect(jsonPath("$.content[*].id", not(hasItem((int) tee))));
		mockMvc.perform(get("/api/v1/products").param("q", "Filtertee").param("color", "Coral"))
			.andExpect(jsonPath("$.content[*].id", hasItem((int) tee)));
		mockMvc.perform(get("/api/v1/products").param("q", "Filtertee").param("color", "Emerald"))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void genderStylesOccasionsAndFlagsFilterTheCatalog() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("gender", "MEN").param("size", "60"))
			.andExpect(jsonPath("$.content[*].gender", everyItem(in(List.of("MEN", "UNISEX")))));
		mockMvc.perform(get("/api/v1/products").param("style", "party-wear").param("category", "women"))
			.andExpect(jsonPath("$.totalElements", greaterThan(0)))
			.andExpect(jsonPath("$.content[*].categorySlug", everyItem(startsWith("women-"))));
		mockMvc.perform(get("/api/v1/products").param("occasion", "festive"))
			.andExpect(jsonPath("$.content[*].categorySlug", hasItem("women-ethnic-wear")));

		String sale = mockMvc.perform(get("/api/v1/products").param("flag", "sale").param("sort", "discount")
			.param("size", "60")).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> onSale = JsonPath.read(sale, "$.content");
		assertThat(onSale).isNotEmpty().allSatisfy(p -> assertThat(new BigDecimal(p.get("compareAtPrice").toString()))
			.isGreaterThan(new BigDecimal(p.get("price").toString())));
		mockMvc.perform(get("/api/v1/products").param("flag", "new"))
			.andExpect(jsonPath("$.totalElements", greaterThan(0)))
			.andExpect(jsonPath("$.content[*].newArrival", everyItem(is(true))));
		mockMvc.perform(get("/api/v1/products").param("flag", "trending"))
			.andExpect(jsonPath("$.content[*].trending", everyItem(is(true))));
	}

	@Test
	void facetsOfferBrandsSizesColorsAndStyles() throws Exception {
		mockMvc.perform(get("/api/v1/products/facets").param("category", "women-jeans"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.brands[*].value", hasItem("Levi's")))
			.andExpect(jsonPath("$.sizes[*].value", hasItems("24", "26", "28")))
			.andExpect(jsonPath("$.colors[?(@.value == 'Mid Wash')].hex").value("#4F6D9A"))
			.andExpect(jsonPath("$.styles[*].value", hasItem("casual")))
			.andExpect(jsonPath("$.attributes[*].key", hasItem("fit")));
		// alpha sizes come in natural order
		mockMvc.perform(get("/api/v1/products/facets").param("category", "women-tops"))
			.andExpect(jsonPath("$.sizes[*].value", contains("XS", "S", "M", "L", "XL")));
	}

	@Test
	void productDetailHasVariantsSizeGuideCareAndMatchingPieces() throws Exception {
		mockMvc.perform(get("/api/v1/products/levi-s-511-slim-jeans"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.product.name").value("Levi's 511 Slim Jeans"))
			.andExpect(jsonPath("$.department.slug").value("men"))
			.andExpect(jsonPath("$.brandInfo.slug").value("levi-s"))
			.andExpect(jsonPath("$.sizeScale").value("WAIST"))
			.andExpect(jsonPath("$.material").value("99% cotton, 1% elastane"))
			.andExpect(jsonPath("$.care", containsString("wash cold")))
			.andExpect(jsonPath("$.variants.length()").value(18)) // 6 sizes × 3 colors
			.andExpect(jsonPath("$.variants[0].size").value("28"))
			.andExpect(jsonPath("$.styles[*].slug", hasItem("casual")))
			.andExpect(jsonPath("$.related[*].categorySlug", everyItem(is("men-jeans"))))
			.andExpect(jsonPath("$.completeTheLook.length()", greaterThan(0)))
			.andExpect(jsonPath("$.completeTheLook[*].categorySlug", not(hasItem("men-jeans"))))
			.andExpect(jsonPath("$.completeTheLook[*].gender", everyItem(in(List.of("MEN", "UNISEX")))));
		mockMvc.perform(get("/api/v1/products/no-such-thing")).andExpect(status().isNotFound());
	}

	@Test
	void outOfStockProductsAreListedWithZeroAvailability() throws Exception {
		long id = createProduct("Soldout Scarf", "15.00", 0);
		String slug = jdbc.sql("SELECT slug FROM products WHERE id = :id").param("id", id).query(String.class).single();
		mockMvc.perform(get("/api/v1/products/" + slug))
			.andExpect(jsonPath("$.product.availableQuantity").value(0))
			.andExpect(jsonPath("$.variants[0].availableQuantity").value(0));
	}

	@Test
	void sortsAndRejectsUnknownParameters() throws Exception {
		String json = mockMvc.perform(get("/api/v1/products").param("category", "watches").param("sort", "price_asc"))
			.andReturn().getResponse().getContentAsString();
		List<Double> prices = JsonPath.read(json, "$.content[*].price");
		assertThat(prices).isNotEmpty().isSortedAccordingTo(Double::compare);
		mockMvc.perform(get("/api/v1/products").param("sort", "cheapest")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/products").param("flag", "free")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_FLAG"));
		mockMvc.perform(get("/api/v1/products").param("gender", "kids")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_GENDER"));
		mockMvc.perform(get("/api/v1/vocabulary"))
			.andExpect(jsonPath("$.styles[*].slug", hasItems("casual", "streetwear", "formal", "minimalist", "party-wear")))
			.andExpect(jsonPath("$.occasions[*].slug", hasItems("weekend", "office", "party", "festive")));
	}

	private void deactivateDefaultVariant(long productId) {
		jdbc.sql("UPDATE product_variants SET active = FALSE WHERE product_id = :id AND size = 'One size'")
			.param("id", productId)
			.update();
	}
}
