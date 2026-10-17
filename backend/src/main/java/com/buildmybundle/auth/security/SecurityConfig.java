package com.buildmybundle.auth.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Session-cookie authentication for the SPA.
 * <ul>
 * <li>Sessions are stored in PostgreSQL (Spring Session JDBC) so they survive restarts and scale out.</li>
 * <li>CSRF uses the cookie-to-header pattern; every state-changing request needs X-XSRF-TOKEN.</li>
 * <li>Login/registration are JSON endpoints in AuthController; logout is handled by the filter chain.</li>
 * <li>Authentication/authorization failures are rendered by GlobalExceptionHandler as problem+json.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

	static final String SESSION_COOKIE = "BMB_SESSION";

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		http.cors(cors -> {
		})
			.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.securityContext(sc -> sc.securityContextRepository(securityContextRepository))
			.requestCache(rc -> rc.disable())
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/api/v1/categories", "/api/v1/products",
						"/api/v1/products/**", "/api/v1/goal-templates", "/api/v1/goal-templates/*",
						"/api/v1/goal-categories", "/api/v1/goals/*", "/api/v1/goals/*/recommendations/latest",
						"/api/v1/recommendations/*", "/api/v1/bundles/*", "/api/v1/bundles/*/alternatives",
						"/api/v1/bundles/*/accessories", "/api/v1/ai/status")
				.permitAll()
				// goals and their recommendations work anonymously; ids are unguessable share links
				.requestMatchers(HttpMethod.POST, "/api/v1/goals", "/api/v1/goals/analyze",
						"/api/v1/goals/*/recommendations", "/api/v1/recommendations/*/explanation")
				.permitAll()
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/v1/**").authenticated()
				.requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**",
						"/swagger-ui.html", "/error")
				.permitAll()
				.anyRequest()
				.denyAll())
			.exceptionHandling(ex -> ex
				// Delegate to @RestControllerAdvice so 401/403 responses use the same problem+json format.
				.authenticationEntryPoint((req, res, e) -> {
					if (exceptionResolver.resolveException(req, res, null, e) == null) {
						new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED).commence(req, res, e);
					}
				})
				.accessDeniedHandler((req, res, e) -> {
					if (exceptionResolver.resolveException(req, res, null, e) == null) {
						res.sendError(HttpStatus.FORBIDDEN.value());
					}
				}))
			.logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
				.invalidateHttpSession(true)
				.deleteCookies(SESSION_COOKIE)
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// bcrypt by default, with {id} prefixes so the algorithm can be upgraded without a migration
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository() {
		// readable by JS (that is the point of cookie-to-header); SameSite=Lax via the default cookie customizer
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookiePath("/");
		return repository;
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** Applied on programmatic login/registration: new session id (fixation protection) and a fresh CSRF token. */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CookieCsrfTokenRepository csrfTokenRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN", "Idempotency-Key"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}
}
