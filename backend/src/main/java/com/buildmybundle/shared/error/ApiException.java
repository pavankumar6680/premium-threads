package com.buildmybundle.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Business error that maps directly to an HTTP problem response.
 * {@code code} is a stable, machine-readable identifier the frontend can switch on; optional
 * {@code properties} are added to the problem body (e.g. which items are out of stock).
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final Map<String, Object> properties = new LinkedHashMap<>();

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message);
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	public ApiException with(String key, Object value) {
		properties.put(key, value);
		return this;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}
}
