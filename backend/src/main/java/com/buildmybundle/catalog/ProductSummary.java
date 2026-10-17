package com.buildmybundle.catalog;

import java.math.BigDecimal;

/**
 * Catalog product as seen by other modules and by listing pages. {@code price} is the current price;
 * {@code active=false} means the product can no longer be bought.
 */
public record ProductSummary(Long id, String sku, String slug, String name, String brand, BigDecimal price,
		BigDecimal ratingAvg, int ratingCount, int qualityTier, String categorySlug, String categoryName,
		boolean active, int availableQuantity) {

	public ProductSummary withAvailableQuantity(int quantity) {
		return new ProductSummary(id, sku, slug, name, brand, price, ratingAvg, ratingCount, qualityTier,
				categorySlug, categoryName, active, quantity);
	}

	/** Active and enough stock for {@code quantity} units. */
	public boolean canFulfil(int quantity) {
		return active && availableQuantity >= quantity;
	}
}
