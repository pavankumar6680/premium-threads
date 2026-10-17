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
	 * @param available false when the product was discontinued or there is not enough stock for {@code quantity}
	 */
	public record Item(Long productId, String sku, String slug, String name, String brand, String categorySlug,
			BigDecimal unitPrice, int quantity, BigDecimal lineTotal, int availableQuantity, boolean available) {
	}
}
