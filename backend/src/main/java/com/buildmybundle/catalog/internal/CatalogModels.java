package com.buildmybundle.catalog.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.buildmybundle.catalog.ProductSummary;

/** Read models returned by the catalog endpoints. */
public final class CatalogModels {

	private CatalogModels() {
	}

	public enum ProductSort {

		RELEVANCE, POPULAR, PRICE_ASC, PRICE_DESC, RATING, NEWEST;

		public static ProductSort parse(String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			try {
				return valueOf(value.trim().toUpperCase());
			}
			catch (IllegalArgumentException ex) {
				return null;
			}
		}
	}

	/**
	 * @param attributes attribute key → accepted values (OR within a key, AND across keys)
	 */
	public record SearchCriteria(String q, String category, List<String> brands, BigDecimal minPrice,
			BigDecimal maxPrice, BigDecimal minRating, List<String> tags, Map<String, List<String>> attributes,
			ProductSort sort, int page, int size) {
	}

	public record CategoryNode(Long id, String slug, String name, String description, long productCount,
			List<CategoryNode> children) {
	}

	public record CategoryRef(String slug, String name) {
	}

	public record AttributeValue(String key, String label, String type, String unit, List<String> values) {
	}

	public record ProductImage(String url, String altText) {
	}

	public record ProductDetail(ProductSummary product, String description, CategoryRef department,
			List<AttributeValue> attributes, List<String> tags, List<ProductImage> images,
			List<ProductSummary> related) {
	}

	public record FacetValue(String value, long count) {
	}

	public record AttributeFacet(String key, String label, String type, String unit, List<FacetValue> values) {
	}

	/**
	 * Filter options for the current category / search scope. Counts reflect the scope (category + text
	 * query), not the other refinements, so options do not disappear while the user is filtering.
	 */
	public record Facets(List<FacetValue> brands, BigDecimal minPrice, BigDecimal maxPrice,
			List<AttributeFacet> attributes) {
	}
}
