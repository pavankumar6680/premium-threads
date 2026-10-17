package com.buildmybundle.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PricingPolicyTest {

	private final PricingPolicy policy = new PricingPolicy(
			new PricingProperties(new BigDecimal("9.99"), new BigDecimal("100.00"), new BigDecimal("0.08")));

	@Test
	void chargesFlatShippingBelowThreshold() {
		PriceBreakdown price = policy.quote(new BigDecimal("40.00"));
		assertThat(price.shipping()).isEqualByComparingTo("9.99");
		assertThat(price.tax()).isEqualByComparingTo("3.20");
		assertThat(price.total()).isEqualByComparingTo("53.19");
	}

	@Test
	void shipsFreeAtOrAboveThreshold() {
		assertThat(policy.quote(new BigDecimal("100.00")).shipping()).isEqualByComparingTo("0.00");
		assertThat(policy.quote(new BigDecimal("105.50")).total()).isEqualByComparingTo("113.94");
	}

	@Test
	void roundsTaxHalfUpToCents() {
		assertThat(policy.quote(new BigDecimal("10.06")).tax()).isEqualByComparingTo("0.80"); // 0.8048
		assertThat(policy.quote(new BigDecimal("10.19")).tax()).isEqualByComparingTo("0.82"); // 0.8152
		assertThat(policy.quote(new BigDecimal("6.25")).tax()).isEqualByComparingTo("0.50"); // 0.5000
		assertThat(policy.quote(new BigDecimal("10.5625")).subtotal()).isEqualByComparingTo("10.56");
	}

	@Test
	void emptyCartCostsNothing() {
		PriceBreakdown price = policy.quote(BigDecimal.ZERO);
		assertThat(price.shipping()).isEqualByComparingTo("0");
		assertThat(price.total()).isEqualByComparingTo("0");
	}
}
