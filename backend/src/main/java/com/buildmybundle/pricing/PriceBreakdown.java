package com.buildmybundle.pricing;

import java.math.BigDecimal;

public record PriceBreakdown(BigDecimal subtotal, BigDecimal shipping, BigDecimal tax, BigDecimal total,
		BigDecimal freeShippingThreshold) {
}
