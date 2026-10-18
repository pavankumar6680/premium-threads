package com.buildmybundle.admin;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.Test;

/** Every admin endpoint requires the ADMIN role: anonymous visitors get 401, customers 403. */
class AdminSecurityIntegrationTest extends IntegrationTest {

	private static final List<String> ADMIN_READS = List.of("/api/v1/admin/products", "/api/v1/admin/brands",
			"/api/v1/admin/categories", "/api/v1/admin/orders", "/api/v1/admin/compatibility-rules",
			"/api/v1/admin/goal-templates", "/api/v1/admin/analytics/summary", "/api/v1/admin/audit-log");

	@Test
	void anonymousVisitorsAreUnauthenticated() throws Exception {
		BrowserSession anonymous = new BrowserSession(mockMvc);
		for (String path : ADMIN_READS) {
			anonymous.get(path).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void customersAreForbiddenToReadOrWrite() throws Exception {
		BrowserSession customer = newCustomer();
		for (String path : ADMIN_READS) {
			customer.get(path).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		customer.putJson("/api/v1/admin/products/1/stock", "{\"quantity\":0}").andExpect(status().isForbidden());
		customer.postJson("/api/v1/admin/orders/1/status", "{\"status\":\"SHIPPED\"}").andExpect(status().isForbidden());
		customer.patchJson("/api/v1/admin/compatibility-rules/1/active", "{\"active\":false}").andExpect(status().isForbidden());
	}

	@Test
	void administratorsCanReadEverything() throws Exception {
		BrowserSession admin = newAdmin();
		for (String path : ADMIN_READS) {
			admin.get(path).andExpect(status().isOk());
		}
	}
}
