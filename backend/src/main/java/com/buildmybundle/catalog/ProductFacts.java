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
