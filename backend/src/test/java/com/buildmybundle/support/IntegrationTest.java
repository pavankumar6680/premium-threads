package com.buildmybundle.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for full-stack tests: real Flyway schema (including the demo catalog) on embedded
 * PostgreSQL, MockMvc through the security filter chain. Tests that change stock or prices create their
 * own products so they never depend on each other.
 */
@SpringBootTest(properties = "app.rate-limit.enabled=false") // RateLimitIntegrationTest covers limiting
@AutoConfigureMockMvc
public abstract class IntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcClient jdbc;

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
		registry.add("spring.datasource.username", () -> "postgres");
		registry.add("spring.datasource.password", () -> "postgres");
	}

	/** Registers a fresh customer and returns a browser session signed in as that customer. */
	protected BrowserSession newCustomer() throws Exception {
		BrowserSession browser = new BrowserSession(mockMvc);
		browser
			.postJson("/api/v1/auth/register",
					"{\"email\":\"user-" + UUID.randomUUID() + "@example.com\",\"password\":\"Passw0rd!\",\"fullName\":\"Test Customer\"}")
			.andExpect(status().isCreated());
		return browser;
	}

	/** Registers a fresh user with the ADMIN role and returns a browser session signed in as that user. */
	protected BrowserSession newAdmin() throws Exception {
		String email = "admin-" + UUID.randomUUID() + "@example.com";
		new BrowserSession(mockMvc)
			.postJson("/api/v1/auth/register", "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\",\"fullName\":\"Test Admin\"}")
			.andExpect(status().isCreated());
		jdbc.sql("""
				INSERT INTO user_roles (user_id, role_id)
				SELECT u.id, r.id FROM users u, roles r WHERE u.email = :email AND r.name = 'ADMIN'
				""").param("email", email).update();
		// roles are read at sign-in, so sign in again in a fresh session
		BrowserSession admin = new BrowserSession(mockMvc);
		admin.postJson("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}")
			.andExpect(status().isOk());
		return admin;
	}

	/**
	 * Creates an active product with its own stock in a dedicated test category, so tests never change the
	 * price ranges or counts of the seeded catalog categories that other tests assert on.
	 */
	protected long createProduct(String name, String price, int stock) {
		jdbc.sql("""
				INSERT INTO categories (parent_id, slug, name, sort_order)
				SELECT id, 'test-items', 'Test items', 99 FROM categories WHERE slug = 'home-office'
				ON CONFLICT (slug) DO NOTHING
				""").update();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long id = jdbc.sql("""
				INSERT INTO products (category_id, sku, slug, name, brand, description, price)
				SELECT id, :sku, :slug, :name, 'Testbrand', 'Integration test product', :price
				FROM categories WHERE slug = 'test-items'
				RETURNING id
				""")
			.param("sku", "TEST-" + suffix)
			.param("slug", "test-" + suffix)
			.param("name", name + " " + suffix)
			.param("price", new BigDecimal(price))
			.query(Long.class)
			.single();
		jdbc.sql("INSERT INTO inventory (product_id, quantity_on_hand) VALUES (:id, :qty)")
			.param("id", id)
			.param("qty", stock)
			.update();
		return id;
	}

	/** Puts {@code quantity} units in the customer's cart and checks out; returns the order id. */
	protected long placeOrder(BrowserSession customer, long productId, int quantity) throws Exception {
		customer.postJson("/api/v1/cart/items", "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}")
			.andExpect(status().is2xxSuccessful());
		long addressId = BrowserSession.longField(
				customer.postJson("/api/v1/me/addresses", addressJson("Test Customer", true)).andExpect(status().isCreated()),
				"$.id");
		return BrowserSession.longField(customer
			.postJson("/api/v1/checkout", "{\"addressId\":" + addressId + "}",
					java.util.Map.of("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isCreated()), "$.id");
	}

	protected int stockOf(long productId) {
		return jdbc.sql("SELECT quantity_on_hand FROM inventory WHERE product_id = :id")
			.param("id", productId)
			.query(Integer.class)
			.single();
	}

	protected void setStock(long productId, int quantity) {
		jdbc.sql("UPDATE inventory SET quantity_on_hand = :qty WHERE product_id = :id")
			.param("qty", quantity)
			.param("id", productId)
			.update();
	}

	protected void setPrice(long productId, String price) {
		jdbc.sql("UPDATE products SET price = :price WHERE id = :id")
			.param("price", new BigDecimal(price))
			.param("id", productId)
			.update();
	}

	protected void deactivate(long productId) {
		jdbc.sql("UPDATE products SET status = 'INACTIVE' WHERE id = :id").param("id", productId).update();
	}

	protected static String addressJson(String fullName, boolean makeDefault) {
		return """
				{"fullName":"%s","line1":"1 Main Street","city":"Springfield","state":"IL","postalCode":"62701",
				 "country":"US","phone":"+1 555 0100","makeDefault":%s}
				""".formatted(fullName, makeDefault);
	}
}
