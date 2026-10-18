package com.buildmybundle.catalog;

/**
 * One purchasable option of a product (a size in a color) with its live stock. Carts, orders and outfits
 * reference variants; the product carries the price.
 *
 * @param colorFamily NEUTRAL, ACCENT, DENIM or METAL, used for outfit color coordination
 */
public record VariantSummary(Long id, Long productId, String sku, String size, String color, String colorHex,
		String colorFamily, boolean active, int availableQuantity) {

	/** Active and enough stock for {@code quantity} units. */
	public boolean canFulfil(int quantity) {
		return active && availableQuantity >= quantity;
	}

	/** "M · Navy", or just the color/size when the other is the default. */
	public String label() {
		boolean defaultSize = "One size".equals(size);
		boolean defaultColor = "Standard".equals(color);
		if (defaultSize && defaultColor) {
			return "";
		}
		return defaultSize ? color : defaultColor ? size : size + " · " + color;
	}
}
