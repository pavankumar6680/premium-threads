package com.buildmybundle.shared.ratelimit;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.GlobalExceptionHandler;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Per-client token-bucket rate limiting for the API (Bucket4j). Runs after Spring Security so signed-in
 * users are limited by account rather than by a shared office IP. Buckets live in a bounded Caffeine cache
 * and expire when idle; this is per instance, which suits a single-node deployment (a shared store such as
 * Redis would be the next step for several instances).
 *
 * <p>
 * A rejected request gets 429 with a problem+json body (code RATE_LIMITED) and a Retry-After header.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

	enum Policy {
		AUTH, AI, ENGINE, CHECKOUT, GENERAL
	}

	private record Route(HttpMethod method, String pattern, Policy policy) {
	}

	private static final List<Route> ROUTES = List.of(
			new Route(HttpMethod.POST, "/api/v1/auth/login", Policy.AUTH),
			new Route(HttpMethod.POST, "/api/v1/auth/register", Policy.AUTH),
			new Route(HttpMethod.POST, "/api/v1/goals/analyze", Policy.AI),
			new Route(HttpMethod.POST, "/api/v1/recommendations/*/explanation", Policy.AI),
			new Route(HttpMethod.POST, "/api/v1/goals/*/recommendations", Policy.ENGINE),
			new Route(HttpMethod.POST, "/api/v1/bundles/*/reoptimize", Policy.ENGINE),
			new Route(HttpMethod.POST, "/api/v1/checkout", Policy.CHECKOUT));

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RateLimitProperties properties;
	private final AntPathMatcher matcher = new AntPathMatcher();
	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
		.maximumSize(100_000)
		.expireAfterAccess(Duration.ofMinutes(10))
		.build();

	public RateLimitFilter(RateLimitProperties properties) {
		this.properties = properties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Policy policy = policy(request);
		String key = policy + ":" + client(request, policy);
		Bucket bucket = buckets.get(key, k -> newBucket(limit(policy)));
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
			chain.doFilter(request, response);
			return;
		}
		long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
		logger.info("Rate limit " + policy + " exceeded by " + key);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", "Too Many Requests");
		body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
		body.put("detail", "Too many requests. Please wait " + retryAfter + " seconds and try again.");
		body.put("instance", request.getRequestURI());
		body.put(GlobalExceptionHandler.CODE, "RATE_LIMITED");
		body.put("retryAfterSeconds", retryAfter);
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader("Retry-After", String.valueOf(retryAfter));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write(JSON.writeValueAsString(body));
	}

	private Policy policy(HttpServletRequest request) {
		String path = request.getRequestURI();
		for (Route route : ROUTES) {
			if (route.method().matches(request.getMethod()) && matcher.match(route.pattern(), path)) {
				return route.policy();
			}
		}
		return Policy.GENERAL;
	}

	private int limit(Policy policy) {
		return switch (policy) {
			case AUTH -> properties.auth();
			case AI -> properties.ai();
			case ENGINE -> properties.engine();
			case CHECKOUT -> properties.checkout();
			case GENERAL -> properties.general();
		};
	}

	/** The signed-in user, or the client IP (already resolved from trusted proxy headers in production). */
	private static String client(HttpServletRequest request, Policy policy) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (policy != Policy.AUTH && auth != null && auth.getPrincipal() instanceof AuditLog.Actor actor) {
			return "user-" + actor.getId();
		}
		return "ip-" + request.getRemoteAddr();
	}

	/** Refills gradually (greedy), so a client is never locked out for a whole minute after a burst. */
	private static Bucket newBucket(int perMinute) {
		return Bucket.builder()
			.addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
			.build();
	}
}
