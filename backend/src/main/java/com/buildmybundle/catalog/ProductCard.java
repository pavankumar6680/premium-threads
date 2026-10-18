package com.buildmybundle.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * A product as shown on listing pages: every {@link ProductSummary} field (same JSON names) plus
 * merchandising data.
 *
 * @param compareAtPrice the price before a reduction, or null when the product is not on sale
 * @param colors colors offered, in display order
 * @param sizes sizes offered (any color), in size order
 * @param inStockSizes sizes with at least one unit in some color
 * @param imageUrl first uploaded photo, or null (the storefront then draws an illustration)
 */
public record ProductCard(Long id, String sku, String slug, String name, String brand, String brandSlug,
		BigDecimal price, BigDecimal compareAtPrice, BigDecimal ratingAvg, int ratingCount, int qualityTier,
		String categorySlug, String categoryName, String gender, boolean active, int availableQuantity,
		boolean newArrival, boolean trending, boolean featured, List<ColorOption> colors, List<String> sizes,
		List<String> inStockSizes, String imageUrl) {

	public boolean onSale() {
		return compareAtPrice != null && compareAtPrice.compareTo(price) > 0;
	}

	/** Whole-percent reduction, or 0. */
	public int discountPercent() {
		if (!onSale()) {
			return 0;
		}
		return compareAtPrice.subtract(price)
			.multiply(BigDecimal.valueOf(100))
			.divide(compareAtPrice, 0, RoundingMode.HALF_UP)
			.intValue();
	}
}
