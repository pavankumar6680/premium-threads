package com.buildmybundle.auth.web;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.auth.service.AuthService;
import com.buildmybundle.auth.web.AuthDtos.LoginRequest;
import com.buildmybundle.auth.web.AuthDtos.RegisterRequest;
import com.buildmybundle.auth.web.AuthDtos.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthService authService;
	private final AuthenticationManager authenticationManager;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	AuthController(AuthService authService, AuthenticationManager authenticationManager,
			SessionAuthenticationStrategy sessionAuthenticationStrategy,
			SecurityContextRepository securityContextRepository) {
		this.authService = authService;
		this.authenticationManager = authenticationManager;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.securityContextRepository = securityContextRepository;
	}

	@Operation(summary = "Issue the XSRF-TOKEN cookie",
			description = "Call once before the first state-changing request; the SPA echoes the cookie in X-XSRF-TOKEN.")
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken(); // forces the deferred token to be generated and written as a cookie
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Register a customer account and sign in")
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		AuthenticatedUser user = authService.registerCustomer(body.email(), body.password(), body.fullName());
		startSession(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()), request,
				response);
		return UserResponse.from(user);
	}

	@Operation(summary = "Sign in with email and password")
	@PostMapping("/login")
	UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken
			.unauthenticated(AuthService.normalizeEmail(body.email()), body.password()));
		startSession(authentication, request, response);
		return UserResponse.from((AuthenticatedUser) authentication.getPrincipal());
	}

	@Operation(summary = "Current user profile", description = "Returns 401 when not signed in.")
	@GetMapping("/me")
	UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
		// Reload so role or name changes are reflected without waiting for a new session.
		return UserResponse.from(authService.loadPrincipal(principal.getId()));
	}

	private void startSession(Authentication authentication, HttpServletRequest request,
			HttpServletResponse response) {
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}
}
