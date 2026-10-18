package com.buildmybundle.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A product with its specification values and tags, as needed for compatibility checks and scoring.
 * Attribute values use their canonical text form (numbers without trailing zeros, booleans as "true"/"false");
 * LIST attributes have several values.
 */
public record ProductFacts(ProductSummary product, Long categoryId, Map<String, List<String>> attributes,
		Set<String> tags) {

	/** Derived attribute: MEN and/or WOMEN (unisex products carry both). */
	public static final String WARDROBE = "wardrobe";
	/** Derived attribute: the part of an outfit a piece covers (upper, lower, outer); absent for accessories. */
	public static final String COVERS = "covers";
	/** Derived attribute: formality of clothing only, so accessories never break a dress code. */
	public static final String DRESS_CODE = "dress_code";
	/** Derived attribute: "size|color" for every option with stock. */
	public static final String STOCK_OPTIONS = "stock_options";
	public static final String OPTION_SEPARATOR = "|";

	/** Names of the attributes the catalog derives for every product (usable in compatibility rules). */
	public static final java.util.Set<String> DERIVED = java.util.Set.of(WARDROBE, COVERS, DRESS_CODE, STOCK_OPTIONS);

	/** True when some color of the given size is in stock (any size when {@code size} is null). */
	public boolean inStockInSize(String size) {
		return values(STOCK_OPTIONS).stream()
			.anyMatch(o -> size == null || o.startsWith(size + OPTION_SEPARATOR));
	}

	/** True when the given color is in stock in the given size (any size when null). */
	public boolean inStock(String size, String color) {
		return values(STOCK_OPTIONS).stream()
			.anyMatch(o -> o.endsWith(OPTION_SEPARATOR + color) && (size == null || o.startsWith(size + OPTION_SEPARATOR)));
	}

	public Long id() {
		return product.id();
	}

	public String categorySlug() {
		return product.categorySlug();
	}

	public List<String> values(String key) {
		return attributes.getOrDefault(key, List.of());
	}

	public Optional<Double> number(String key) {
		List<String> values = values(key);
		if (values.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Double.parseDouble(values.getFirst()));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}
}
