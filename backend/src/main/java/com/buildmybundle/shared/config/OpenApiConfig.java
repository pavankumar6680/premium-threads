package com.buildmybundle.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	@Bean
	OpenAPI buildMyBundleOpenApi() {
		return new OpenAPI().info(new Info().title("Premium Threads API")
			.version("v1")
			.description("Goal-based e-commerce: catalog, bundles, recommendations, cart and orders. "
					+ "Authentication uses a session cookie; mutating requests require the X-XSRF-TOKEN header "
					+ "echoing the XSRF-TOKEN cookie (call GET /api/v1/auth/csrf first)."));
	}
}
