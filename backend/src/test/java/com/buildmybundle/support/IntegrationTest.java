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
	 * Creates an active product sold in a single option ("One size"), with its own stock, in a dedicated test
	 * category, so tests never change the price ranges or counts of the seeded catalog categories.
	 */
	protected long createProduct(String name, String price, int stock) {
		jdbc.sql("""
				INSERT INTO categories (parent_id, slug, name, sort_order, outfit_slot, size_scale)
				SELECT id, 'test-items', 'Test items', 99, 'ACCESSORY', 'ONE_SIZE' FROM categories WHERE slug = 'accessories'
				ON CONFLICT (slug) DO NOTHING
				""").update();
		jdbc.sql("INSERT INTO brands (slug, name) VALUES ('testbrand', 'Testbrand') ON CONFLICT (slug) DO NOTHING").update();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long id = jdbc.sql("""
				INSERT INTO products (category_id, brand_id, sku, slug, name, brand, description, price)
				SELECT c.id, b.id, :sku, :slug, :name, b.name, 'Integration test product', :price
				FROM categories c, brands b WHERE c.slug = 'test-items' AND b.slug = 'testbrand'
				RETURNING id
				""")
			.param("sku", "TEST-" + suffix)
			.param("slug", "test-" + suffix)
			.param("name", name + " " + suffix)
			.param("price", new BigDecimal(price))
			.query(Long.class)
			.single();
		long variantId = jdbc.sql("""
				INSERT INTO product_variants (product_id, sku, size, color) VALUES (:id, :sku, 'One size', 'Standard')
				RETURNING id
				""").param("id", id).param("sku", "TEST-" + suffix).query(Long.class).single();
		jdbc.sql("INSERT INTO variant_stock (variant_id, quantity_on_hand) VALUES (:id, :qty)")
			.param("id", variantId)
			.param("qty", stock)
			.update();
		return id;
	}

	/** Adds a size × color variant with its own stock to a product; returns the variant id. */
	protected long addVariant(long productId, String size, String color, int stock) {
		long variantId = jdbc.sql("""
				INSERT INTO product_variants (product_id, sku, size, color, color_hex)
				SELECT id, sku || '-' || :size || '-' || :color, :size, :color, '#336699' FROM products WHERE id = :id
				RETURNING id
				""").param("id", productId).param("size", size).param("color", color).query(Long.class).single();
		jdbc.sql("INSERT INTO variant_stock (variant_id, quantity_on_hand) VALUES (:id, :qty)")
			.param("id", variantId)
			.param("qty", stock)
			.update();
		return variantId;
	}

	/** The single variant of a product created with {@link #createProduct}. */
	protected long variantOf(long productId) {
		return jdbc.sql("SELECT id FROM product_variants WHERE product_id = :id ORDER BY id LIMIT 1")
			.param("id", productId)
			.query(Long.class)
			.single();
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

	/** Total stock of a product (all variants). */
	protected int stockOf(long productId) {
		return jdbc.sql("""
				SELECT coalesce(sum(s.quantity_on_hand), 0) FROM product_variants v
				JOIN variant_stock s ON s.variant_id = v.id WHERE v.product_id = :id
				""")
			.param("id", productId)
			.query(Integer.class)
			.single();
	}

	/** Sets the stock of a single-variant product. */
	protected void setStock(long productId, int quantity) {
		jdbc.sql("UPDATE variant_stock SET quantity_on_hand = :qty WHERE variant_id = :id")
			.param("qty", quantity)
			.param("id", variantOf(productId))
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
