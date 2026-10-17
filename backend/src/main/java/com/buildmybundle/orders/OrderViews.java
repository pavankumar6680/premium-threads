package com.buildmybundle.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.buildmybundle.orders.domain.ShippingAddress;
import com.buildmybundle.payments.PaymentReceipt;

/** API representations of orders. */
public final class OrderViews {

	private OrderViews() {
	}

	public record OrderView(Long id, String orderNumber, String status, Instant createdAt, Instant cancelledAt,
			List<Item> items, BigDecimal subtotal, BigDecimal shipping, BigDecimal tax, BigDecimal total,
			String currency, ShippingAddress shippingAddress, PaymentReceipt payment, boolean cancellable) {
	}

	/** {@code slug} is the product's current slug for linking; name and price are the purchase-time snapshot. */
	public record Item(Long productId, String slug, String sku, String name, String brand, BigDecimal unitPrice,
			int quantity, BigDecimal lineTotal) {
	}

	public record OrderSummary(Long id, String orderNumber, String status, Instant createdAt, BigDecimal total,
			String currency, int itemCount, List<String> itemNames) {
	}
}
