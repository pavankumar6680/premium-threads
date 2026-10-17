package com.buildmybundle.bundles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.buildmybundle.compatibility.CompatibilityModels.Report;

/** Public types of the bundles module. */
public final class BundleModels {

	private BundleModels() {
	}

	public enum BundleTier {
		BUDGET, BALANCED, PREMIUM, CUSTOM
	}

	public record NewItem(Long productId, Long categoryId, int quantity, BigDecimal unitPrice, boolean optional,
			String explanation) {
	}

	public record NewBundle(UUID goalId, UUID runId, Long userId, BundleTier tier, String name, BigDecimal budget,
			BigDecimal totalPrice, BigDecimal score, String summary, List<NewItem> items) {
	}

	/**
	 * @param unitPrice price when the item was chosen
	 * @param currentPrice today's price (null if the product no longer exists)
	 * @param available active and enough stock for the quantity
	 */
	public record ItemView(Long productId, String slug, String sku, String name, String brand, String categorySlug,
			String categoryName, int quantity, BigDecimal unitPrice, BigDecimal currentPrice, BigDecimal lineTotal,
			boolean optional, boolean locked, String explanation, BigDecimal ratingAvg, int ratingCount,
			int availableQuantity, boolean available) {
	}

	/**
	 * @param totalPrice total when the bundle was last changed
	 * @param currentTotal total at today's prices
	 * @param compatibility live re-check of every rule against the current catalog data
	 * @param purchasable every item available and no blocking incompatibility
	 * @param editable the viewer owns this bundle and it is not a (read-only) recommendation
	 */
	public record BundleView(UUID id, UUID goalId, String goalTitle, UUID runId, UUID sourceBundleId, BundleTier tier,
			String name, String status, BigDecimal budget, BigDecimal totalPrice, BigDecimal currentTotal,
			String summary, List<ItemView> items, Report compatibility, boolean purchasable, boolean editable,
			Instant createdAt, Instant updatedAt) {
	}

	public record BundleSummary(UUID id, String name, BundleTier tier, String status, UUID goalId, String goalTitle,
			BigDecimal budget, BigDecimal totalPrice, int itemCount, Instant updatedAt) {
	}

	/**
	 * A product that could fill a bundle slot.
	 *
	 * @param priceDelta change of the bundle total if this product replaced the current one
	 * @param issues why it would conflict with the rest of the bundle (empty when compatible)
	 */
	public record Alternative(Long productId, String slug, String name, String brand, BigDecimal price,
			BigDecimal priceDelta, BigDecimal ratingAvg, int ratingCount, int availableQuantity, boolean inStock,
			boolean compatible, List<String> issues, boolean current) {
	}

	/** A category worth adding, with its best compatible products. */
	public record AccessorySuggestion(String categorySlug, String categoryName, String reason,
			List<Alternative> products) {
	}

	public record PurchaseLine(Long productId, int quantity) {
	}

	/** What the optimizer needs to re-optimize a customer's bundle. */
	public record EditableBundle(UUID id, UUID goalId, BundleTier tier, BigDecimal budget, List<Slot> slots) {

		public record Slot(Long categoryId, Long productId, int quantity, boolean optional, boolean locked) {
		}
	}

	/** New product for a slot, chosen by the optimizer. */
	public record SlotChoice(Long categoryId, Long productId, BigDecimal unitPrice, String explanation) {
	}
}
