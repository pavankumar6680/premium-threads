package com.buildmybundle.auth;

import java.io.Serial;
import java.util.Collection;
import java.util.Set;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The security principal stored in the session. Part of the auth module's public API: other modules
 * obtain the current user with {@code @AuthenticationPrincipal AuthenticatedUser user}.
 * Deliberately a small immutable snapshot (not the JPA entity) because it is serialized into the session store.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Long id;
	private final String email;
	private final String fullName;
	private final Set<String> roles;
	private final boolean enabled;
	private String passwordHash;

	public AuthenticatedUser(Long id, String email, String fullName, Set<String> roles, boolean enabled,
			String passwordHash) {
		this.id = id;
		this.email = email;
		this.fullName = fullName;
		this.roles = Set.copyOf(roles);
		this.enabled = enabled;
		this.passwordHash = passwordHash;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getFullName() {
		return fullName;
	}

	public Set<String> getRoles() {
		return roles;
	}

	public boolean hasRole(String role) {
		return roles.contains(role);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}
}
