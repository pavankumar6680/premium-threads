package com.buildmybundle.catalog.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.buildmybundle.catalog.ProductSummary;

/** Read models returned by the catalog endpoints. */
public final class CatalogModels {

	private CatalogModels() {
	}

	public enum ProductSort {

		RELEVANCE, POPULAR, PRICE_ASC, PRICE_DESC, RATING, NEWEST, DISCOUNT;

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
	 * @param brands brand names or slugs
	 * @param sizes sizes that must be in stock (in any color)
	 * @param colors color names (in any size)
	 * @param gender MEN or WOMEN (unisex products always match)
	 * @param flags any of sale, new, trending, featured (all must hold)
	 * @param attributes attribute key → accepted values (OR within a key, AND across keys)
	 */
	public record SearchCriteria(String q, String category, List<String> brands, BigDecimal minPrice,
			BigDecimal maxPrice, BigDecimal minRating, List<String> tags, Map<String, List<String>> attributes,
			List<String> sizes, List<String> colors, List<String> styles, List<String> occasions, String gender,
			List<String> flags, ProductSort sort, int page, int size) {
	}

	public record ColorOption(String name, String hex) {
	}

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

	public record CategoryNode(Long id, String slug, String name, String description, long productCount,
			List<CategoryNode> children) {
	}

	public record CategoryRef(String slug, String name) {
	}

	public record BrandRef(String slug, String name, String description) {
	}

	public record AttributeValue(String key, String label, String type, String unit, List<String> values) {
	}

	/** @param color the color this photo shows, or null when it applies to every color */
	public record ProductImage(String url, String altText, String color) {
	}

	public record Variant(Long id, String sku, String size, String color, String colorHex, int availableQuantity) {
	}

	public record NamedRef(String slug, String name) {
	}

	/**
	 * @param sizeScale ALPHA, WAIST or ONE_SIZE (which size guide applies)
	 * @param completeTheLook pieces from other categories that share a style and wardrobe
	 */
	public record ProductDetail(ProductCard product, String description, CategoryRef department, BrandRef brandInfo,
			String sizeScale, String material, String care, List<Variant> variants, List<NamedRef> styles,
			List<NamedRef> occasions, List<AttributeValue> attributes, List<String> tags, List<ProductImage> images,
			List<ProductCard> related, List<ProductCard> completeTheLook) {
	}

	public record FacetValue(String value, long count) {
	}

	public record ColorFacet(String value, String hex, long count) {
	}

	public record AttributeFacet(String key, String label, String type, String unit, List<FacetValue> values) {
	}

	/**
	 * Filter options for the current scope (category, text query, gender, tags). Counts ignore the other
	 * refinements, so options do not disappear while the shopper is filtering.
	 */
	public record Facets(List<FacetValue> brands, BigDecimal minPrice, BigDecimal maxPrice, List<FacetValue> sizes,
			List<ColorFacet> colors, List<FacetValue> styles, List<AttributeFacet> attributes) {
	}

	/** A brand with its active product count and the categories it sells in. */
	public record BrandSummary(Long id, String slug, String name, String description, boolean featured,
			long productCount, List<String> categories) {
	}

	/** Styles and occasions offered for filtering and the outfit builder. */
	public record Vocabulary(List<NamedRef> styles, List<NamedRef> occasions) {
	}

	// ------------------------------------------------------------------ internal rows

	/** A product row with the merchandising columns (before variants and stock are attached). */
	record CardRow(ProductSummary summary, String brandSlug, BigDecimal compareAtPrice, String gender,
			boolean newArrival, boolean trending, boolean featured) {
	}

	record VariantRow(Long id, Long productId, String sku, String size, String color, String colorHex,
			String colorFamily, boolean active) {
	}
}
