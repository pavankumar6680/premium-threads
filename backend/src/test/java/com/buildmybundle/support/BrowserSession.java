package com.buildmybundle.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Drives MockMvc the way the SPA's browser does: keeps a cookie jar (session + XSRF-TOKEN), fetches a
 * CSRF token before the first state-changing request and echoes it in the X-XSRF-TOKEN header.
 * Exercises the real CSRF configuration instead of spring-security-test's csrf() shortcut.
 */
public final class BrowserSession {

	public static final String CSRF_COOKIE = "XSRF-TOKEN";
	public static final String SESSION_COOKIE = "BMB_SESSION";

	private final MockMvc mockMvc;
	private final Map<String, Cookie> jar = new LinkedHashMap<>();

	public BrowserSession(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	public ResultActions get(String path) throws Exception {
		return exchange(MockMvcRequestBuilders.get(path));
	}

	public ResultActions postJson(String path, String json) throws Exception {
		return postJson(path, json, Map.of());
	}

	public ResultActions postJson(String path, String json, Map<String, String> headers) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(path)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json);
		headers.forEach(request::header);
		return mutate(request);
	}

	public ResultActions post(String path) throws Exception {
		return mutate(MockMvcRequestBuilders.post(path));
	}

	public ResultActions putJson(String path, String json) throws Exception {
		return mutate(MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	public ResultActions patchJson(String path, String json) throws Exception {
		return mutate(MockMvcRequestBuilders.patch(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	public ResultActions multipart(String path, org.springframework.mock.web.MockMultipartFile file) throws Exception {
		ensureCsrfToken();
		var request = MockMvcRequestBuilders.multipart(path).file(file)
			.header("X-XSRF-TOKEN", jar.get(CSRF_COOKIE).getValue())
			.cookie(jar.values().toArray(Cookie[]::new));
		ResultActions result = mockMvc.perform(request);
		store(result.andReturn().getResponse());
		return result;
	}

	public ResultActions put(String path) throws Exception {
		return mutate(MockMvcRequestBuilders.put(path));
	}

	public ResultActions delete(String path) throws Exception {
		return mutate(MockMvcRequestBuilders.delete(path));
	}

	/**
	 * Adds this session's cookies and CSRF header to a request without recording the response, so the
	 * request can be performed from another thread (concurrency tests).
	 */
	public MockHttpServletRequestBuilder prepare(MockHttpServletRequestBuilder request) throws Exception {
		ensureCsrfToken();
		return request.cookie(jar.values().toArray(Cookie[]::new))
			.header("X-XSRF-TOKEN", jar.get(CSRF_COOKIE).getValue());
	}

	public Optional<Cookie> cookie(String name) {
		return Optional.ofNullable(jar.get(name));
	}

	/** Reads a numeric JSON field from a response. */
	public static long longField(ResultActions result, String jsonPath) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath)).longValue();
	}

	private ResultActions mutate(MockHttpServletRequestBuilder request) throws Exception {
		ensureCsrfToken();
		return exchange(request.header("X-XSRF-TOKEN", jar.get(CSRF_COOKIE).getValue()));
	}

	private void ensureCsrfToken() throws Exception {
		if (!jar.containsKey(CSRF_COOKIE)) {
			exchange(MockMvcRequestBuilders.get("/api/v1/auth/csrf"));
		}
	}

	private ResultActions exchange(MockHttpServletRequestBuilder request) throws Exception {
		if (!jar.isEmpty()) {
			request.cookie(jar.values().toArray(Cookie[]::new));
		}
		ResultActions result = mockMvc.perform(request);
		store(result.andReturn().getResponse());
		return result;
	}

	private void store(MockHttpServletResponse response) {
		for (Cookie cookie : response.getCookies()) {
			if (cookie.getMaxAge() == 0) {
				jar.remove(cookie.getName());
			}
			else {
				jar.put(cookie.getName(), cookie);
			}
		}
	}
}
