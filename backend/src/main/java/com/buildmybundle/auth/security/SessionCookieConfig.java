package com.buildmybundle.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Session cookie attributes, defined explicitly so they are identical in every runtime (embedded server,
 * WAR, MockMvc). Boot only applies server.servlet.session.cookie.* on an embedded server.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookieConfig {

	@Bean
	CookieSerializer sessionCookieSerializer(@Value("${app.session.secure-cookie:false}") boolean secure) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SecurityConfig.SESSION_COOKIE);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(secure);
		return serializer;
	}
}
