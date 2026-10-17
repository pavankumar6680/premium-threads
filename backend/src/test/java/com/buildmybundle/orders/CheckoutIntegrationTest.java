package com.buildmybundle.orders;

import static com.buildmybundle.support.BrowserSession.longField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CheckoutIntegrationTest extends IntegrationTest {

	private BrowserSession customer;
	private long addressId;
	private long lamp; // 40.00, stock 5
	private long chair; // 25.50, stock 2

	@BeforeEach
	void setUp() throws Exception {
		customer = newCustomer();
		addressId = createAddress(customer);
		lamp = createProduct("Lamp", "40.00", 5);
		chair = createProduct("Chair", "25.50", 2);
	}

	@Test
	void checkoutCreatesPaidOrderDecrementsStockAndEmptiesCart() throws Exception {
		addToCart(customer, lamp, 2);
		addToCart(customer, chair, 1);

		ResultActions placed = checkout(customer, newKey()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderNumber", startsWith("BMB-")))
			.andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.subtotal").value(105.50))
			.andExpect(jsonPath("$.shipping").value(0.00))
			.andExpect(jsonPath("$.tax").value(8.44))
			.andExpect(jsonPath("$.total").value(113.94))
			.andExpect(jsonPath("$.shippingAddress.city").value("Springfield"))
			.andExpect(jsonPath("$.payment.status").value("APPROVED"))
			.andExpect(jsonPath("$.payment.method").value("SIMULATED_CARD"))
			.andExpect(jsonPath("$.cancellable").value(true));
		long orderId = longField(placed, "$.id");

		assertThat(stockOf(lamp)).isEqualTo(3);
		assertThat(stockOf(chair)).isEqualTo(1);
		customer.get("/api/v1/cart").andExpect(jsonPath("$.items", hasSize(0)));
		customer.get("/api/v1/orders")
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(orderId))
			.andExpect(jsonPath("$.content[0].itemCount").value(3));
	}

	@Test
	void orderKeepsPurchaseTimePricesWhenCatalogPricesChange() throws Exception {
		addToCart(customer, lamp, 1);
		long orderId = longField(checkout(customer, newKey()).andExpect(status().isCreated()), "$.id");

		setPrice(lamp, "99.00");

		customer.get("/api/v1/orders/" + orderId)
			.andExpect(jsonPath("$.items[0].unitPrice").value(40.00))
			.andExpect(jsonPath("$.subtotal").value(40.00))
			.andExpect(jsonPath("$.shipping").value(9.99))
			.andExpect(jsonPath("$.total").value(53.19));
	}

	@Test
	void retryingWithTheSameKeyReturnsTheOriginalOrderWithoutChargingAgain() throws Exception {
		addToCart(customer, lamp, 1);
		String key = newKey();
		long first = longField(checkout(customer, key).andExpect(status().isCreated()), "$.id");
		long second = longField(checkout(customer, key).andExpect(status().isCreated()), "$.id");

		assertThat(second).isEqualTo(first);
		assertThat(stockOf(lamp)).isEqualTo(4);
		customer.get("/api/v1/orders").andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void simultaneousDuplicateSubmissionsCreateExactlyOneOrder() throws Exception {
		addToCart(customer, lamp, 1);
		String key = newKey();

		List<MockHttpServletResponse> responses = runConcurrently(
				() -> checkoutRequest(customer, key), () -> checkoutRequest(customer, key));

		List<String> ids = new ArrayList<>();
		for (MockHttpServletResponse response : responses) {
			assertThat(response.getStatus()).isEqualTo(201);
			ids.add(com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.orderNumber"));
		}
		assertThat(ids.get(0)).isEqualTo(ids.get(1));
		assertThat(stockOf(lamp)).isEqualTo(4);
	}

	@Test
	void twoCustomersCannotBothBuyTheLastUnit() throws Exception {
		long lastUnit = createProduct("Last unit", "10.00", 1);
		BrowserSession other = newCustomer();
		long otherAddress = createAddress(other);
		addToCart(customer, lastUnit, 1);
		addToCart(other, lastUnit, 1);

		List<MockHttpServletResponse> responses = runConcurrently(
				() -> checkoutRequest(customer, newKey(), addressId), () -> checkoutRequest(other, newKey(), otherAddress));

		List<Integer> statuses = responses.stream().map(MockHttpServletResponse::getStatus).sorted().toList();
		assertThat(statuses).containsExactly(201, 409);
		assertThat(stockOf(lastUnit)).isZero();
	}

	@Test
	void insufficientStockRollsBackTheWholeCheckout() throws Exception {
		addToCart(customer, lamp, 2);
		addToCart(customer, chair, 2);
		setStock(chair, 1); // someone else bought one in the meantime

		checkout(customer, newKey()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
			.andExpect(jsonPath("$.shortages[0].productId").value(chair))
			.andExpect(jsonPath("$.shortages[0].available").value(1));

		assertThat(stockOf(lamp)).as("lamp reservation rolled back").isEqualTo(5);
		customer.get("/api/v1/cart").andExpect(jsonPath("$.items", hasSize(2)));
		customer.get("/api/v1/orders").andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void validatesCheckoutPreconditions() throws Exception {
		customer.postJson("/api/v1/checkout", "{\"addressId\":" + addressId + "}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

		checkout(customer, newKey()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CART_EMPTY"));

		addToCart(customer, lamp, 1);
		BrowserSession other = newCustomer();
		long foreignAddress = createAddress(other);
		customer.postJson("/api/v1/checkout", "{\"addressId\":" + foreignAddress + "}", Map.of("Idempotency-Key", newKey()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));

		deactivate(lamp);
		checkout(customer, newKey()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
	}

	@Test
	void cancellingRestocksAndRefundsOnlyOnce() throws Exception {
		addToCart(customer, lamp, 2);
		long orderId = longField(checkout(customer, newKey()), "$.id");
		assertThat(stockOf(lamp)).isEqualTo(3);

		customer.post("/api/v1/orders/" + orderId + "/cancel")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.payment.status").value("REFUNDED"))
			.andExpect(jsonPath("$.cancellable").value(false));
		assertThat(stockOf(lamp)).isEqualTo(5);

		customer.post("/api/v1/orders/" + orderId + "/cancel")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
		assertThat(stockOf(lamp)).isEqualTo(5);
	}

	@Test
	void ordersArePrivateToTheirOwner() throws Exception {
		addToCart(customer, lamp, 1);
		long orderId = longField(checkout(customer, newKey()), "$.id");

		BrowserSession other = newCustomer();
		other.get("/api/v1/orders/" + orderId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
		other.post("/api/v1/orders/" + orderId + "/cancel").andExpect(status().isNotFound());
	}

	private long createAddress(BrowserSession browser) throws Exception {
		return longField(browser.postJson("/api/v1/me/addresses", addressJson("Test Customer", true))
			.andExpect(status().isCreated()), "$.id");
	}

	private static void addToCart(BrowserSession browser, long productId, int quantity) throws Exception {
		browser.postJson("/api/v1/cart/items", "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}")
			.andExpect(status().isOk());
	}

	private ResultActions checkout(BrowserSession browser, String key) throws Exception {
		return browser.postJson("/api/v1/checkout", "{\"addressId\":" + addressId + "}", Map.of("Idempotency-Key", key));
	}

	private MockHttpServletRequestBuilder checkoutRequest(BrowserSession browser, String key) throws Exception {
		return checkoutRequest(browser, key, addressId);
	}

	private static MockHttpServletRequestBuilder checkoutRequest(BrowserSession browser, String key, long address)
			throws Exception {
		return browser.prepare(post("/api/v1/checkout").contentType(MediaType.APPLICATION_JSON)
			.content("{\"addressId\":" + address + "}")
			.header("Idempotency-Key", key));
	}

	/** Fires the requests at the same moment from separate threads. */
	@SafeVarargs
	private List<MockHttpServletResponse> runConcurrently(Callable<MockHttpServletRequestBuilder>... builders)
			throws Exception {
		List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
		for (Callable<MockHttpServletRequestBuilder> builder : builders) {
			requests.add(builder.call());
		}
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService pool = Executors.newFixedThreadPool(requests.size())) {
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (MockHttpServletRequestBuilder request : requests) {
				futures.add(pool.submit(() -> {
					start.await();
					return mockMvc.perform(request).andReturn().getResponse();
				}));
			}
			start.countDown();
			List<MockHttpServletResponse> responses = new ArrayList<>();
			for (Future<MockHttpServletResponse> future : futures) {
				responses.add(future.get());
			}
			return responses;
		}
	}

	private static String newKey() {
		return UUID.randomUUID().toString();
	}
}
