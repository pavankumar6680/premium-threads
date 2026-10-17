package com.buildmybundle.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminOrderIntegrationTest extends IntegrationTest {

	private BrowserSession admin;
	private BrowserSession customer;
	private long product;

	@BeforeEach
	void setUp() throws Exception {
		admin = newAdmin();
		customer = newCustomer();
		product = createProduct("Fulfilment Lamp", "30.00", 10);
	}

	@Test
	void ordersMoveFromPaidToShippedToDelivered() throws Exception {
		long orderId = placeOrder(customer, product, 2);

		admin.get("/api/v1/admin/orders?status=PAID")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].id", hasItem((int) orderId)));
		admin.get("/api/v1/admin/orders/" + orderId)
			.andExpect(jsonPath("$.order.status").value("PAID"))
			.andExpect(jsonPath("$.customer.fullName").value("Test Customer"))
			.andExpect(jsonPath("$.allowedTransitions[0]").value("SHIPPED"));

		admin.postJson("/api/v1/admin/orders/" + orderId + "/status", "{\"status\":\"SHIPPED\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.order.status").value("SHIPPED"))
			.andExpect(jsonPath("$.allowedTransitions[0]").value("DELIVERED"));
		// a shipped order can no longer be cancelled by the customer
		customer.post("/api/v1/orders/" + orderId + "/cancel").andExpect(status().isConflict());
		customer.get("/api/v1/orders/" + orderId).andExpect(jsonPath("$.status").value("SHIPPED"));

		admin.postJson("/api/v1/admin/orders/" + orderId + "/status", "{\"status\":\"DELIVERED\"}")
			.andExpect(jsonPath("$.order.status").value("DELIVERED"))
			.andExpect(jsonPath("$.allowedTransitions.length()").value(0));
		admin.postJson("/api/v1/admin/orders/" + orderId + "/status", "{\"status\":\"SHIPPED\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
		admin.get("/api/v1/admin/audit-log?entityType=ORDER")
			.andExpect(jsonPath("$.content[0].action").value("ORDER_DELIVERED"));
	}

	@Test
	void staffCancellationRestocksAndRefunds() throws Exception {
		long orderId = placeOrder(customer, product, 3);
		assertThat(stockOf(product)).isEqualTo(7);

		admin.postJson("/api/v1/admin/orders/" + orderId + "/status", "{\"status\":\"CANCELLED\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.order.status").value("CANCELLED"))
			.andExpect(jsonPath("$.order.payment.status").value("REFUNDED"));
		assertThat(stockOf(product)).isEqualTo(10);
		admin.postJson("/api/v1/admin/orders/" + orderId + "/status", "{\"status\":\"CANCELLED\"}")
			.andExpect(status().isConflict()); // never restocked twice
		assertThat(stockOf(product)).isEqualTo(10);
	}

	@Test
	void findsOrdersByNumberAndRejectsBadInput() throws Exception {
		long orderId = placeOrder(customer, product, 1);
		String number = customer.get("/api/v1/orders/" + orderId).andReturn().getResponse().getContentAsString()
			.replaceAll(".*\"orderNumber\":\"([^\"]+)\".*", "$1");

		admin.get("/api/v1/admin/orders?orderNumber=" + number.toLowerCase())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(orderId));
		admin.get("/api/v1/admin/orders?status=LOST").andExpect(status().isBadRequest());
		admin.postJson("/api/v1/admin/orders/" + orderId + "/status", "{\"status\":\"PAID\"}").andExpect(status().isBadRequest());
		admin.get("/api/v1/admin/orders/999999").andExpect(status().isNotFound());
	}
}
