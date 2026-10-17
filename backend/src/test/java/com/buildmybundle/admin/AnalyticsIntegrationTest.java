package com.buildmybundle.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

class AnalyticsIntegrationTest extends IntegrationTest {

	@Test
	void summaryReflectsNewOrdersAndExcludesCancelledOnes() throws Exception {
		BrowserSession admin = newAdmin();
		BrowserSession customer = newCustomer();
		long product = createProduct("Analytics Speaker", "100.00", 10);
		String before = admin.get("/api/v1/admin/analytics/summary?days=7").andReturn().getResponse().getContentAsString();

		long kept = placeOrder(customer, product, 2);
		long cancelled = placeOrder(customer, product, 1);
		customer.post("/api/v1/orders/" + cancelled + "/cancel").andExpect(status().isOk());

		String after = admin.get("/api/v1/admin/analytics/summary?days=7")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.daily.length()").value(7))
			.andExpect(jsonPath("$.ordersByStatus.CANCELLED").isNumber())
			.andReturn().getResponse().getContentAsString();

		BigDecimal orderTotal = new BigDecimal(customer.get("/api/v1/orders/" + kept).andReturn().getResponse()
			.getContentAsString().replaceAll(".*\"total\":([0-9.]+).*", "$1"));
		assertThat(number(after, "$.sales.revenue").subtract(number(before, "$.sales.revenue"))).isEqualByComparingTo(orderTotal);
		assertThat(number(after, "$.sales.orders").subtract(number(before, "$.sales.orders"))).isEqualByComparingTo("1");
		assertThat(number(after, "$.sales.cancelledOrders").subtract(number(before, "$.sales.cancelledOrders"))).isEqualByComparingTo("1");
		assertThat(number(after, "$.sales.unitsSold").subtract(number(before, "$.sales.unitsSold"))).isEqualByComparingTo("2");
		assertThat(number(after, "$.daily[6].revenue")).isGreaterThanOrEqualTo(orderTotal); // today is the last point
		assertThat(JsonPath.<java.util.List<Object>>read(after, "$.topProducts[?(@.productId == " + product + ")]")).hasSize(1);
		assertThat(number(after, "$.customers.newInPeriod")).isPositive();
	}

	@Test
	void reportsEngineHealthAndLowStock() throws Exception {
		BrowserSession admin = newAdmin();
		long product = createProduct("AlmostGone", "5.00", 1);
		admin.get("/api/v1/admin/analytics/summary?days=90")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inventory.lowStock").isNumber())
			.andExpect(jsonPath("$.recommendations.infeasibleShare").isNumber())
			.andExpect(jsonPath("$.goals.byTemplate").isArray());
		assertThat(number(admin.get("/api/v1/admin/analytics/summary?days=90").andReturn().getResponse().getContentAsString(),
				"$.inventory.lowStock")).isPositive();
		admin.get("/api/v1/admin/products?lowStock=true&q=AlmostGone").andExpect(jsonPath("$.content[0].id").value(product));
		admin.get("/api/v1/admin/analytics/summary?days=12").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
	}

	private static BigDecimal number(String json, String path) {
		return new BigDecimal(JsonPath.read(json, path).toString());
	}
}
