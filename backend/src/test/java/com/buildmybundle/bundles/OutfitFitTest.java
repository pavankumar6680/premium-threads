package com.buildmybundle.bundles;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.buildmybundle.bundles.OutfitFit.Piece;
import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.catalog.VariantSummary;
import com.buildmybundle.goals.GoalModels.Fit;

import org.junit.jupiter.api.Test;

class OutfitFitTest {

	private static long nextId = 1;

	@Test
	void choosesTheWearersSizeAndPreferredColor() {
		Piece tee = piece(1L, "TOP", "ALPHA", v(1L, "M", "White", "NEUTRAL", 5), v(1L, "M", "Navy", "NEUTRAL", 5),
				v(1L, "L", "Navy", "NEUTRAL", 5));
		Map<Long, VariantSummary> chosen = OutfitFit.chooseVariants(List.of(tee),
				new Fit("MEN", "M", null, List.of("Navy")));
		assertThat(chosen.get(1L).size()).isEqualTo("M");
		assertThat(chosen.get(1L).color()).isEqualTo("Navy");
	}

	@Test
	void usesAtMostOneAccentColorAcrossTheOutfit() {
		Piece top = piece(1L, "TOP", "ALPHA", v(1L, "M", "Coral", "ACCENT", 9));
		Piece bottom = piece(2L, "BOTTOM", "WAIST", v(2L, "32", "Mustard", "ACCENT", 9), v(2L, "32", "Indigo", "DENIM", 1));
		Piece cap = piece(3L, "ACCESSORY", "ONE_SIZE", v(3L, "One size", "Coral", "ACCENT", 2),
				v(3L, "One size", "Emerald", "ACCENT", 9));
		Map<Long, VariantSummary> chosen = OutfitFit.chooseVariants(List.of(cap, bottom, top),
				new Fit("MEN", "M", "32", List.of()));
		assertThat(chosen.get(1L).color()).isEqualTo("Coral");
		assertThat(chosen.get(2L).color()).as("base color over a second accent").isEqualTo("Indigo");
		assertThat(chosen.get(3L).color()).as("repeats the outfit's accent").isEqualTo("Coral");
		assertThat(OutfitFit.paletteNote(List.copyOf(chosen.values())))
			.isEqualTo("Colors: a base of Indigo with Coral as the accent.");
	}

	@Test
	void leavesTheSizeToTheShopperWhenUnknown() {
		Piece jeans = piece(1L, "BOTTOM", "WAIST", v(1L, "30", "Indigo", "DENIM", 5), v(1L, "32", "Indigo", "DENIM", 5));
		assertThat(OutfitFit.chooseVariants(List.of(jeans), Fit.NONE)).isEmpty();
		Piece watch = piece(2L, "ACCESSORY", "ONE_SIZE", v(2L, "One size", "Silver", "METAL", 5));
		assertThat(OutfitFit.chooseVariants(List.of(watch), Fit.NONE)).containsKey(2L);
		// nothing in stock in the wearer's size
		Piece soldOut = piece(3L, "TOP", "ALPHA", v(3L, "M", "Black", "NEUTRAL", 0), v(3L, "L", "Black", "NEUTRAL", 4));
		assertThat(OutfitFit.chooseVariants(List.of(soldOut), new Fit(null, "M", null, List.of()))).isEmpty();
	}

	@Test
	void fitsChecksWardrobeAndStockInSize() {
		ProductFacts womensTop = facts(List.of("WOMEN"), List.of("S|Black", "M|White"));
		assertThat(OutfitFit.fits(womensTop, new Fit("WOMEN", "M", null, List.of()), "ALPHA")).isTrue();
		assertThat(OutfitFit.fits(womensTop, new Fit("MEN", "M", null, List.of()), "ALPHA")).isFalse();
		assertThat(OutfitFit.fits(womensTop, new Fit("WOMEN", "XL", null, List.of()), "ALPHA")).isFalse();
		assertThat(OutfitFit.offersPreferredColor(womensTop, new Fit("WOMEN", "M", null, List.of("White")), "ALPHA"))
			.isTrue();
		assertThat(OutfitFit.offersPreferredColor(womensTop, new Fit("WOMEN", "M", null, List.of("Black")), "ALPHA"))
			.as("black is only in stock in S").isFalse();
	}

	private static Piece piece(Long productId, String slot, String scale, VariantSummary... options) {
		return new Piece(productId, slot, scale, 1, List.of(options));
	}

	private static VariantSummary v(Long productId, String size, String color, String family, int stock) {
		long id = nextId++;
		return new VariantSummary(id, productId, "SKU-" + id, size, color, "#000000", family, true, stock);
	}

	private static ProductFacts facts(List<String> wardrobe, List<String> stockOptions) {
		ProductSummary summary = new ProductSummary(1L, "SKU-1", "top", "Top", "Zara", new BigDecimal("30.00"),
				BigDecimal.ZERO, 0, 2, "women-tops", "Tops", true, 10);
		return new ProductFacts(summary, 1L,
				Map.of(ProductFacts.WARDROBE, wardrobe, ProductFacts.STOCK_OPTIONS, stockOptions), Set.of());
	}
}
