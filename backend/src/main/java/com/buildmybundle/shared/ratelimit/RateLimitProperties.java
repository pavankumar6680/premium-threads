package com.buildmybundle.shared.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Requests allowed per client per minute, by policy. A client is the signed-in user, or the IP address for
 * anonymous requests; sign-in and registration are always limited per IP so rotating accounts does not help.
 *
 * @param auth sign-in and registration (brute-force protection)
 * @param ai goal analysis and AI overviews (expensive local model calls)
 * @param engine recommendation runs and re-optimisation (CPU-bound search)
 * @param checkout order placement
 * @param general every other API request
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled, @DefaultValue("10") int auth,
		@DefaultValue("10") int ai, @DefaultValue("30") int engine, @DefaultValue("10") int checkout,
		@DefaultValue("300") int general) {
}
