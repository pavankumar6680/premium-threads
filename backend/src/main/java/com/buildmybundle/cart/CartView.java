package com.buildmybundle.cart;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cart with live prices and server-computed totals.
 *
 * @param checkoutReady true when the cart is non-empty and every line can be fulfilled
 */
public record CartView(List<Item> items, int itemCount, BigDecimal subtotal, BigDecimal shipping, BigDecimal tax,
		BigDecimal total, BigDecimal freeShippingThreshold, boolean checkoutReady) {

	/**
	 * @param sku the variant's SKU
	 * @param availableQuantity stock of this variant
	 * @param available false when the product or variant was discontinued or there is not enough stock
	 */
	public record Item(Long productId, Long variantId, String sku, String slug, String name, String brand,
			String categorySlug, String size, String color, String colorHex, BigDecimal unitPrice, int quantity,
			BigDecimal lineTotal, int availableQuantity, boolean available) {
	}
}
