package com.buildmybundle.cart;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buildmybundle.support.BrowserSession;
import com.buildmybundle.support.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartIntegrationTest extends IntegrationTest {

	private BrowserSession customer;
	private long lamp;
	private long chair;

	@BeforeEach
	void setUp() throws Exception {
		customer = newCustomer();
		lamp = createProduct("Lamp", "40.00", 5);
		chair = createProduct("Chair", "25.50", 2);
	}

	@Test
	void cartRequiresSignIn() throws Exception {
		mockMvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
	}

	@Test
	void addingItemsMergesLinesAndComputesTotalsOnTheServer() throws Exception {
		add(lamp, 1).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
		add(lamp, 1).andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].quantity").value(2));
		add(chair, 1).andExpect(status().isOk())
			.andExpect(jsonPath("$.itemCount").value(3))
			.andExpect(jsonPath("$.items[1].lineTotal").value(25.50))
			.andExpect(jsonPath("$.subtotal").value(105.50))
			.andExpect(jsonPath("$.shipping").value(0.00))
			.andExpect(jsonPath("$.tax").value(8.44))
			.andExpect(jsonPath("$.total").value(113.94))
			.andExpect(jsonPath("$.checkoutReady").value(true));
	}

	@Test
	void pricesAreAlwaysLiveNeverStoredInTheCart() throws Exception {
		add(lamp, 1);
		setPrice(lamp, "35.00");
		customer.get("/api/v1/cart").andExpect(jsonPath("$.items[0].unitPrice").value(35.00));
	}

	@Test
	void cannotAddMoreThanAvailableStock() throws Exception {
		add(chair, 2).andExpect(status().isOk());
		add(chair, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
			.andExpect(jsonPath("$.available").value(2));
	}

	@Test
	void updatesAndRemovesLines() throws Exception {
		add(lamp, 1);
		add(chair, 1);
		customer.putJson("/api/v1/cart/items/" + lamp, "{\"quantity\":4}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity").value(4));
		customer.putJson("/api/v1/cart/items/" + lamp, "{\"quantity\":6}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
		customer.delete("/api/v1/cart/items/" + lamp)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)));
		customer.delete("/api/v1/cart/items/" + lamp)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
	}

	@Test
	void flagsLinesThatCanNoLongerBeFulfilled() throws Exception {
		add(lamp, 3);
		setStock(lamp, 1);
		customer.get("/api/v1/cart")
			.andExpect(jsonPath("$.items[0].available").value(false))
			.andExpect(jsonPath("$.items[0].availableQuantity").value(1))
			.andExpect(jsonPath("$.checkoutReady").value(false));
	}

	@Test
	void rejectsUnknownDiscontinuedAndInvalidQuantities() throws Exception {
		add(999_999_999L, 1).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		deactivate(chair);
		add(chair, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
		add(lamp, 0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void wishlistAddIsIdempotentAndListsProducts() throws Exception {
		customer.put("/api/v1/wishlist/" + lamp).andExpect(status().isNoContent());
		customer.put("/api/v1/wishlist/" + lamp).andExpect(status().isNoContent());
		customer.put("/api/v1/wishlist/" + chair).andExpect(status().isNoContent());
		customer.get("/api/v1/wishlist")
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].id").value(chair)); // most recent first
		customer.delete("/api/v1/wishlist/" + chair).andExpect(status().isNoContent());
		customer.get("/api/v1/wishlist").andExpect(jsonPath("$", hasSize(1)));
		customer.put("/api/v1/wishlist/999999999")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.ResultActions add(long productId, int quantity) throws Exception {
		return customer.postJson("/api/v1/cart/items",
				"{\"productId\":" + productId + ",\"quantity\":" + quantity + "}");
	}
}
