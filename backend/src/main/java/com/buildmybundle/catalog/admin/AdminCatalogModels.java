package com.buildmybundle.catalog.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requests and views of the catalog administration API. */
public final class AdminCatalogModels {

	public static final String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";
	public static final String TAG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

	private AdminCatalogModels() {
	}

	public enum AttributeType {
		TEXT, NUMBER, BOOL, LIST
	}

	/** One row of the admin product table. */
	public record AdminProductRow(Long id, String sku, String slug, String name, String brand, BigDecimal price,
			boolean active, int qualityTier, BigDecimal ratingAvg, int ratingCount, String categorySlug,
			String categoryName, int stock, Instant updatedAt) {
	}

	public record AdminProduct(Long id, String sku, String slug, String name, String brand, String description,
			BigDecimal price, int qualityTier, boolean active, String categorySlug, int stock, List<String> tags,
			Map<String, List<String>> attributes, BigDecimal ratingAvg, int ratingCount, long version,
			Instant updatedAt) {
	}

	/**
	 * Create or update a product. {@code attributes} maps attribute keys of the category to values (one
	 * value, or several for LIST attributes). {@code version} is required on update (optimistic locking);
	 * {@code stock} is used on create only (stock changes afterwards go through the stock endpoint).
	 */
	public record ProductRequest(@NotBlank @Size(max = 80) String categorySlug,
			@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+", message = "letters, digits and dashes only") String sku,
			@NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 80) String brand,
			@Size(max = 5000) String description,
			@NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
			@NotNull @Min(1) @Max(3) Integer qualityTier, @NotNull Boolean active,
			@Size(max = 20) List<@Pattern(regexp = TAG_PATTERN, message = "lowercase words joined by dashes") @Size(max = 40) String> tags,
			@Size(max = 40) Map<String, List<String>> attributes, Long version,
			@Min(0) @Max(1_000_000) Integer stock) {
	}

	public record StockRequest(@NotNull @Min(0) @Max(1_000_000) Integer quantity) {
	}

	public record StatusRequest(@NotNull Boolean active) {
	}

	public record AttributeDefinition(String key, String label, AttributeType type, String unit, boolean filterable) {
	}

	public record AdminCategory(Long id, String slug, String name, String description, String departmentSlug,
			String departmentName, boolean leaf, long productCount, List<AttributeDefinition> attributes) {
	}

	public record CategoryRequest(@NotBlank @Size(max = 80) String departmentSlug,
			@NotBlank @Size(min = 2, max = 80) @Pattern(regexp = SLUG_PATTERN, message = "lowercase words joined by dashes") String slug,
			@NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {
	}

	public record AttributeRequest(
			@NotBlank @Size(max = 60) @Pattern(regexp = "[a-z0-9_]+", message = "lowercase letters, digits and underscores") String key,
			@NotBlank @Size(max = 80) String label, @NotNull AttributeType type, @Size(max = 20) String unit,
			Boolean filterable) {
	}
}
