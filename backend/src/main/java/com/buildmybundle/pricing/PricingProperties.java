package com.buildmybundle.pricing;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Simulated store pricing rules (app.pricing.*). */
@ConfigurationProperties("app.pricing")
public record PricingProperties(@DefaultValue("9.99") BigDecimal flatShipping,
		@DefaultValue("100.00") BigDecimal freeShippingThreshold, @DefaultValue("0.08") BigDecimal taxRate) {
}
