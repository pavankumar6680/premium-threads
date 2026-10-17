package com.buildmybundle.admin;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs with rate limiting on and tiny limits (the other integration tests switch it off). */
class RateLimitIntegrationTest extends IntegrationTest {

	@DynamicPropertySource
	static void limits(DynamicPropertyRegistry registry) {
		registry.add("app.rate-limit.enabled", () -> "true");
		registry.add("app.rate-limit.auth", () -> "3");
		registry.add("app.rate-limit.ai", () -> "2");
		registry.add("app.rate-limit.general", () -> "1000");
	}

	@Test
	void signInAttemptsAreLimitedPerAddress() throws Exception {
		String wrong = "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";
		for (int i = 0; i < 3; i++) {
			// a new browser each time: limits follow the client address, not the session
			new BrowserSession(mockMvc).postJson("/api/v1/auth/login", wrong).andExpect(status().isUnauthorized());
		}
		new BrowserSession(mockMvc).postJson("/api/v1/auth/login", wrong)
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
			.andExpect(jsonPath("$.retryAfterSeconds").isNumber());
		// other endpoints are unaffected
		new BrowserSession(mockMvc).get("/api/v1/categories").andExpect(status().isOk())
			.andExpect(header().exists("X-RateLimit-Remaining"));
	}

	@Test
	void aiCallsAreLimitedPerClient() throws Exception {
		BrowserSession visitor = new BrowserSession(mockMvc);
		String body = "{\"text\":\"a desk for 300 dollars\"}";
		// the anonymous visitor shares the address with the sign-in test above, but AI has its own budget
		visitor.postJson("/api/v1/goals/analyze", body).andExpect(status().isOk());
		visitor.postJson("/api/v1/goals/analyze", body).andExpect(status().isOk());
		visitor.postJson("/api/v1/goals/analyze", body).andExpect(status().isTooManyRequests());
	}
}
