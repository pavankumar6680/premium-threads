package com.buildmybundle.catalog.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
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

	/** One row of the admin product table; {@code stock} is the total over active variants. */
	public record AdminProductRow(Long id, String sku, String slug, String name, String brand, BigDecimal price,
			BigDecimal compareAtPrice, boolean active, int qualityTier, BigDecimal ratingAvg, int ratingCount,
			String categorySlug, String categoryName, String gender, boolean featured, int stock, int variantCount,
			Instant updatedAt) {
	}

	public record AdminVariant(Long id, String sku, String size, String color, String colorHex, String colorFamily,
			boolean active, int stock) {
	}

	public record AdminProduct(Long id, String sku, String slug, String name, String brand, String brandSlug,
			String description, BigDecimal price, BigDecimal compareAtPrice, int qualityTier, boolean active,
			String categorySlug, String gender, boolean featured, boolean newArrival, boolean trending, String material,
			String care, int stock, List<AdminVariant> variants, List<String> styles, List<String> occasions,
			List<String> tags, Map<String, List<String>> attributes, BigDecimal ratingAvg, int ratingCount, long version,
			Instant updatedAt) {
	}

	/**
	 * A size × color option. On update, {@code id} identifies an existing variant (its SKU never changes); a
	 * variant without id is created. {@code stock} applies to new variants only (then use the stock endpoint).
	 */
	public record VariantInput(Long id, @NotBlank @Size(max = 12) String size, @NotBlank @Size(max = 40) String color,
			@Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "a color like #1F2A44") String colorHex,
			@Pattern(regexp = "NEUTRAL|ACCENT|DENIM|METAL") String colorFamily, Boolean active,
			@Min(0) @Max(1_000_000) Integer stock) {
	}

	/**
	 * Create or update a product. {@code attributes} maps attribute keys of the category to values (one value,
	 * or several for LIST attributes). {@code version} is required on update (optimistic locking).
	 *
	 * <p>
	 * Merchandising fields (gender, compareAtPrice, flags, material, care, styles, occasions) and
	 * {@code variants} are optional: on update, null keeps the current value; on create, defaults apply. Without
	 * variants, a product gets a single "One size" option holding {@code stock}. Variants left out of an update
	 * list are deactivated, never deleted (orders reference them).
	 *
	 * @param brand brand name or slug of an existing brand
	 */
	public record ProductRequest(@NotBlank @Size(max = 80) String categorySlug,
			@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9-]+", message = "letters, digits and dashes only") String sku,
			@NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 80) String brand,
			@Size(max = 5000) String description,
			@NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
			@DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal compareAtPrice,
			@NotNull @Min(1) @Max(3) Integer qualityTier, @NotNull Boolean active,
			@Pattern(regexp = "MEN|WOMEN|UNISEX") String gender, Boolean featured, Boolean newArrival, Boolean trending,
			@Size(max = 300) String material, @Size(max = 500) String care,
			@Size(max = 10) List<@NotBlank String> styles, @Size(max = 10) List<@NotBlank String> occasions,
			@Size(max = 20) List<@Pattern(regexp = TAG_PATTERN, message = "lowercase words joined by dashes") @Size(max = 40) String> tags,
			@Size(max = 40) Map<String, List<String>> attributes, @Size(max = 120) List<@Valid VariantInput> variants,
			Long version, @Min(0) @Max(1_000_000) Integer stock) {
	}

	/** Create or update a brand. Renaming a brand renames it on its products too. */
	public record BrandRequest(@NotBlank @Size(max = 80) String name,
			@Size(min = 2, max = 80) @Pattern(regexp = SLUG_PATTERN, message = "lowercase words joined by dashes") String slug,
			@Size(max = 1000) String description, Boolean featured, Boolean active) {
	}

	public record AdminBrand(Long id, String slug, String name, String description, boolean featured, boolean active,
			long productCount) {
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
