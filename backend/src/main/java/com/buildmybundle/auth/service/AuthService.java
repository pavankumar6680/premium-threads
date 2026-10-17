package com.buildmybundle.auth.service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.auth.domain.Role;
import com.buildmybundle.auth.domain.RoleName;
import com.buildmybundle.auth.domain.RoleRepository;
import com.buildmybundle.auth.domain.User;
import com.buildmybundle.auth.domain.UserRepository;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository users;
	private final RoleRepository roles;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.roles = roles;
		this.passwordEncoder = passwordEncoder;
	}

	/** Registers a new customer account and returns its principal (without credentials). */
	@Transactional
	public AuthenticatedUser registerCustomer(String email, String rawPassword, String fullName) {
		User user = createUser(email, rawPassword, fullName, Set.of(RoleName.CUSTOMER));
		AuthenticatedUser principal = toPrincipal(user);
		principal.eraseCredentials();
		return principal;
	}

	/** Creates a user with the given roles. Fails with 409 EMAIL_TAKEN if the email is already registered. */
	@Transactional
	public User createUser(String email, String rawPassword, String fullName, Set<RoleName> roleNames) {
		String normalizedEmail = normalizeEmail(email);
		if (users.existsByEmail(normalizedEmail)) {
			throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
		}
		User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), fullName.trim());
		roleNames.forEach(name -> user.addRole(role(name)));
		// flush so a concurrent duplicate surfaces here as a constraint violation, inside this transaction
		return users.saveAndFlush(user);
	}

	@Transactional(readOnly = true)
	public AuthenticatedUser loadPrincipal(Long userId) {
		User user = users.findById(userId)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User account no longer exists."));
		AuthenticatedUser principal = toPrincipal(user);
		principal.eraseCredentials();
		return principal;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	static AuthenticatedUser toPrincipal(User user) {
		Set<String> roleNames = user.getRoles()
			.stream()
			.map(r -> r.getName().name())
			.collect(Collectors.toUnmodifiableSet());
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(), roleNames, user.isEnabled(),
				user.getPasswordHash());
	}

	private Role role(RoleName name) {
		return roles.findByName(name).orElseThrow(() -> new IllegalStateException("Role not seeded: " + name));
	}
}
