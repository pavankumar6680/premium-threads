package com.buildmybundle.auth;

import static com.buildmybundle.support.BrowserSession.CSRF_COOKIE;
import static com.buildmybundle.support.BrowserSession.SESSION_COOKIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthIntegrationTest extends IntegrationTest {

	private BrowserSession browser;

	@BeforeEach
	void newBrowser() {
		browser = new BrowserSession(mockMvc);
	}

	@Test
	void registerCreatesCustomerAndStartsHardenedSession() throws Exception {
		String email = uniqueEmail();
		register(email, "Passw0rd!", "Ada Lovelace").andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
			.andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(cookie().httpOnly(SESSION_COOKIE, true))
			.andExpect(header().stringValues("Set-Cookie",
					hasItem(allOf(containsString(SESSION_COOKIE + "="), containsString("SameSite=Lax")))));

		browser.get("/api/v1/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void registerNormalizesEmailAndRejectsDuplicates() throws Exception {
		String email = uniqueEmail();
		register(email.toUpperCase(), "Passw0rd!", "First").andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email));

		new BrowserSession(mockMvc).postJson("/api/v1/auth/register", json(email, "Passw0rd!", "Second"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void registerValidatesInput() throws Exception {
		register("not-an-email", "short", "").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'fullName')]").exists());
	}

	@Test
	void loginFailuresDoNotRevealWhetherTheAccountExists() throws Exception {
		String email = uniqueEmail();
		new BrowserSession(mockMvc).postJson("/api/v1/auth/register", json(email, "Passw0rd!", "Grace Hopper"))
			.andExpect(status().isCreated());

		login(email, "WrongPass1").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		login(uniqueEmail(), "Passw0rd!").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void loginRotatesCsrfTokenAndLogoutInvalidatesSession() throws Exception {
		String email = uniqueEmail();
		new BrowserSession(mockMvc).postJson("/api/v1/auth/register", json(email, "Passw0rd!", "Alan Turing"))
			.andExpect(status().isCreated());

		login(email, "Passw0rd!").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
		Cookie session = browser.cookie(SESSION_COOKIE).orElseThrow();
		assertThat(browser.cookie(CSRF_COOKIE)).as("pre-login CSRF token is discarded").isEmpty();

		browser.get("/api/v1/auth/me").andExpect(status().isOk());
		browser.post("/api/v1/auth/logout").andExpect(status().isNoContent());

		// replaying the old session cookie must not work
		mockMvc.perform(get("/api/v1/auth/me").cookie(session))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void anonymousRequestsToProtectedEndpointsGet401ProblemDetails() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void stateChangingRequestWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json(uniqueEmail(), "Passw0rd!", null)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void csrfEndpointIssuesTokenCookieReadableBySpa() throws Exception {
		browser.get("/api/v1/auth/csrf")
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists(CSRF_COOKIE))
			.andExpect(cookie().httpOnly(CSRF_COOKIE, false));
	}

	@Test
	void adminEndpointsRequireAdminRole() throws Exception {
		mockMvc.perform(get("/api/v1/admin/anything")).andExpect(status().isUnauthorized());

		register(uniqueEmail(), "Passw0rd!", "Customer").andExpect(status().isCreated());
		browser.get("/api/v1/admin/anything")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	private ResultActions register(String email, String password, String fullName) throws Exception {
		return browser.postJson("/api/v1/auth/register", json(email, password, fullName));
	}

	private ResultActions login(String email, String password) throws Exception {
		return browser.postJson("/api/v1/auth/login", json(email, password, null));
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String json(String email, String password, String fullName) {
		StringBuilder sb = new StringBuilder("{\"email\":\"").append(email)
			.append("\",\"password\":\"")
			.append(password)
			.append('"');
		if (fullName != null) {
			sb.append(",\"fullName\":\"").append(fullName).append('"');
		}
		return sb.append('}').toString();
	}
}
