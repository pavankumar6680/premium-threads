package com.buildmybundle.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * Live availability of a category: price range and brands consider only active products that are in stock,
 * so they describe what can actually be bought right now. Prices are null when nothing is in stock.
 */
public record CategoryStats(Long categoryId, long productCount, long inStockCount, BigDecimal minPrice,
		BigDecimal maxPrice, List<String> brands) {

	public static CategoryStats empty(Long categoryId) {
		return new CategoryStats(categoryId, 0, 0, null, null, List.of());
	}
}
