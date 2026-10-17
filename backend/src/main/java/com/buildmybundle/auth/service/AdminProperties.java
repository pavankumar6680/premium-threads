package com.buildmybundle.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional bootstrap administrator account (app.admin.*). Disabled when email or password is blank. */
@ConfigurationProperties("app.admin")
public record AdminProperties(String email, String password, String fullName) {

	boolean isConfigured() {
		return email != null && !email.isBlank() && password != null && !password.isBlank();
	}
}
