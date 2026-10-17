package com.buildmybundle.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * The single place where shipping, tax and totals are computed. Used for cart estimates and for the
 * authoritative order totals at checkout, so both always agree.
 */
@Component
public class PricingPolicy {

	private final PricingProperties properties;

	public PricingPolicy(PricingProperties properties) {
		this.properties = properties;
	}

	public PriceBreakdown quote(BigDecimal subtotal) {
		BigDecimal goods = money(subtotal);
		BigDecimal shipping = goods.signum() == 0 || goods.compareTo(properties.freeShippingThreshold()) >= 0
				? money(BigDecimal.ZERO) : money(properties.flatShipping());
		BigDecimal tax = money(goods.multiply(properties.taxRate()));
		return new PriceBreakdown(goods, shipping, tax, goods.add(shipping).add(tax),
				money(properties.freeShippingThreshold()));
	}

	public static BigDecimal money(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.HALF_UP);
	}
}
