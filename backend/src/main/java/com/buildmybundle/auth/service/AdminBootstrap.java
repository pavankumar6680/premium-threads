package com.buildmybundle.auth.service;

import java.util.Set;

import com.buildmybundle.auth.domain.RoleName;
import com.buildmybundle.auth.domain.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the configured administrator on startup if it does not exist yet. Credentials come from
 * configuration (ADMIN_EMAIL / ADMIN_PASSWORD), never from a migration, so no default admin password
 * ships in the schema.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminProperties properties;
	private final UserRepository users;
	private final AuthService authService;

	AdminBootstrap(AdminProperties properties, UserRepository users, AuthService authService) {
		this.properties = properties;
		this.users = users;
		this.authService = authService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured()) {
			return;
		}
		String email = AuthService.normalizeEmail(properties.email());
		if (users.existsByEmail(email)) {
			return;
		}
		String name = properties.fullName() == null || properties.fullName().isBlank() ? "Store Administrator"
				: properties.fullName();
		authService.createUser(email, properties.password(), name, Set.of(RoleName.ADMIN, RoleName.CUSTOMER));
		log.info("Created bootstrap administrator account {}", email);
	}
}
