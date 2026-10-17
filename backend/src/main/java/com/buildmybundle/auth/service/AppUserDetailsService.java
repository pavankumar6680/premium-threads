package com.buildmybundle.auth.service;

import com.buildmybundle.auth.domain.UserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AppUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	AppUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		return users.findByEmail(AuthService.normalizeEmail(email))
			.map(AuthService::toPrincipal)
			.orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
	}
}
