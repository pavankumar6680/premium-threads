package com.buildmybundle.catalog.admin;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminProduct;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminProductRow;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AttributeType;
import com.buildmybundle.catalog.admin.AdminCatalogModels.ProductRequest;
import com.buildmybundle.inventory.InventoryService;
import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product administration: create, edit, price, stock and status. Every change is audited.
 *
 * <p>
 * Edits use optimistic locking on {@code products.version}: an update states the version it was based on
 * and is refused with 409 if someone else changed the product meanwhile, so two admins cannot silently
 * overwrite each other. Attribute values are validated against the category's attribute definitions,
 * because compatibility rules and filters read them.
 */
@Service
public class ProductAdminService {

	public static final int LOW_STOCK_THRESHOLD = 5;
	private static final String ENTITY = "PRODUCT";

	private final JdbcClient jdbc;
	private final InventoryService inventory;
	private final AuditLog audit;

	public ProductAdminService(JdbcClient jdbc, InventoryService inventory, AuditLog audit) {
		this.jdbc = jdbc;
		this.inventory = inventory;
		this.audit = audit;
	}

	public enum Sort {
		NAME, PRICE, UPDATED, STOCK
	}

	private record Definition(Long id, String key, AttributeType type) {
	}

	private record Current(Long id, String sku, String name, BigDecimal price, boolean active, Long categoryId,
			long version) {
	}

	// ---------------------------------------------------------------- queries

	@Transactional(readOnly = true)
	public PageResponse<AdminProductRow> list(String q, String category, Boolean active, boolean lowStock, Sort sort,
			int page, int size) {
		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		Map<String, Object> params = new HashMap<>();
		if (q != null && !q.isBlank()) {
			where.append(" AND (p.name ILIKE :q OR p.sku ILIKE :q OR p.brand ILIKE :q)");
			params.put("q", "%" + q.trim().replace("%", "\\%").replace("_", "\\_") + "%");
		}
		if (category != null && !category.isBlank()) {
			where.append(" AND c.slug = :category");
			params.put("category", category);
		}
		if (active != null) {
			where.append(" AND p.status = :status");
			params.put("status", active ? "ACTIVE" : "INACTIVE");
		}
		String from = " FROM products p JOIN categories c ON c.id = p.category_id";
		if (lowStock) {
			// stock belongs to the inventory module: ask it which of the matching products are running low
			List<Long> ids = jdbc.sql("SELECT p.id" + from + where).paramSource(params).query(Long.class).list();
			List<Long> low = inventory.productIdsWithStockAtMost(ids, LOW_STOCK_THRESHOLD);
			if (low.isEmpty()) {
				return PageResponse.of(List.of(), page, size, 0);
			}
			where.append(" AND p.id IN (:low)");
			params.put("low", low);
		}
		long total = jdbc.sql("SELECT count(*)" + from + where).paramSource(params).query(Long.class).single();
		if (total == 0) {
			return PageResponse.of(List.of(), page, size, 0);
		}
		List<AdminProductRow> rows;
		String select = """
				SELECT p.id, p.sku, p.slug, p.name, p.brand, p.price, p.status, p.quality_tier, p.rating_avg,
				       p.rating_count, c.slug AS category_slug, c.name AS category_name, p.updated_at""" + from + where;
		if (sort == Sort.STOCK) {
			// sorted by a value owned by another module: page in memory over the (filtered) id set
			List<AdminProductRow> all = withStock(jdbc.sql(select).paramSource(params).query(this::row).list());
			rows = all.stream()
				.sorted(java.util.Comparator.comparingInt(AdminProductRow::stock).thenComparing(AdminProductRow::name))
				.skip((long) page * size)
				.limit(size)
				.toList();
		}
		else {
			String order = switch (sort == null ? Sort.NAME : sort) {
				case PRICE -> " ORDER BY p.price, p.id";
				case UPDATED -> " ORDER BY p.updated_at DESC, p.id DESC";
				default -> " ORDER BY p.name, p.id";
			};
			params.put("limit", size);
			params.put("offset", page * size);
			rows = withStock(jdbc.sql(select + order + " LIMIT :limit OFFSET :offset")
				.paramSource(params)
				.query(this::row)
				.list());
		}
		return PageResponse.of(rows, page, size, total);
	}

	@Transactional(readOnly = true)
	public AdminProduct get(Long id) {
		return jdbc.sql("""
				SELECT p.id, p.sku, p.slug, p.name, p.brand, p.description, p.price, p.quality_tier, p.status,
				       c.slug AS category_slug, p.rating_avg, p.rating_count, p.version, p.updated_at
				FROM products p JOIN categories c ON c.id = p.category_id WHERE p.id = :id
				""").param("id", id).query((rs, n) -> new AdminProduct(rs.getLong("id"), rs.getString("sku"),
				rs.getString("slug"), rs.getString("name"), rs.getString("brand"), rs.getString("description"),
				rs.getBigDecimal("price"), rs.getInt("quality_tier"), "ACTIVE".equals(rs.getString("status")),
				rs.getString("category_slug"), inventory.availableQuantity(id), tags(id), attributes(id),
				rs.getBigDecimal("rating_avg"), rs.getInt("rating_count"), rs.getLong("version"),
				rs.getTimestamp("updated_at").toInstant()))
			.optional()
			.orElseThrow(ProductAdminService::notFound);
	}

	// ---------------------------------------------------------------- commands

	@Transactional
	public AdminProduct create(ProductRequest request) {
		Long categoryId = leafCategoryId(request.categorySlug());
		String sku = request.sku().trim().toUpperCase(Locale.ROOT);
		if (jdbc.sql("SELECT count(*) FROM products WHERE sku = :sku").param("sku", sku).query(Long.class).single() > 0) {
			throw ApiException.conflict("SKU_TAKEN", "Another product already uses SKU " + sku + ".").with("field", "sku");
		}
		Map<Definition, List<Object>> attributes = validateAttributes(categoryId, request.attributes());
		Long id = jdbc.sql("""
				INSERT INTO products (category_id, sku, slug, name, brand, description, price, quality_tier, status)
				VALUES (:categoryId, :sku, :slug, :name, :brand, :description, :price, :tier, :status)
				RETURNING id
				""")
			.param("categoryId", categoryId)
			.param("sku", sku)
			.param("slug", uniqueSlug(request.name(), sku))
			.param("name", request.name().trim())
			.param("brand", request.brand().trim())
			.param("description", Objects.requireNonNullElse(request.description(), "").trim())
			.param("price", request.price())
			.param("tier", request.qualityTier())
			.param("status", request.active() ? "ACTIVE" : "INACTIVE")
			.query(Long.class)
			.single();
		writeAttributes(id, attributes);
		writeTags(id, request.tags());
		inventory.setStock(id, Objects.requireNonNullElse(request.stock(), 0));
		audit.record("PRODUCT_CREATED", ENTITY, id,
				Map.of("sku", sku, "name", request.name().trim(), "price", request.price(), "category", request.categorySlug()));
		return get(id);
	}

	@Transactional
	public AdminProduct update(Long id, ProductRequest request) {
		if (request.version() == null) {
			throw ApiException.badRequest("VERSION_REQUIRED", "Send the version of the product you edited.")
				.with("field", "version");
		}
		Current before = current(id);
		Long categoryId = leafCategoryId(request.categorySlug());
		String sku = request.sku().trim().toUpperCase(Locale.ROOT);
		if (jdbc.sql("SELECT count(*) FROM products WHERE sku = :sku AND id <> :id")
			.param("sku", sku)
			.param("id", id)
			.query(Long.class)
			.single() > 0) {
			throw ApiException.conflict("SKU_TAKEN", "Another product already uses SKU " + sku + ".").with("field", "sku");
		}
		Map<Definition, List<Object>> attributes = validateAttributes(categoryId, request.attributes());
		int updated = jdbc.sql("""
				UPDATE products SET category_id = :categoryId, sku = :sku, name = :name, brand = :brand,
				       description = :description, price = :price, quality_tier = :tier, status = :status,
				       updated_at = now(), version = version + 1
				WHERE id = :id AND version = :version
				""")
			.param("categoryId", categoryId)
			.param("sku", sku)
			.param("name", request.name().trim())
			.param("brand", request.brand().trim())
			.param("description", Objects.requireNonNullElse(request.description(), "").trim())
			.param("price", request.price())
			.param("tier", request.qualityTier())
			.param("status", request.active() ? "ACTIVE" : "INACTIVE")
			.param("id", id)
			.param("version", request.version())
			.update();
		if (updated == 0) {
			throw ApiException.conflict("STALE_PRODUCT",
					"Someone else changed this product after you opened it. Reload it and apply your changes again.")
				.with("currentVersion", before.version());
		}
		jdbc.sql("DELETE FROM product_attributes WHERE product_id = :id").param("id", id).update();
		writeAttributes(id, attributes);
		jdbc.sql("DELETE FROM product_tags WHERE product_id = :id").param("id", id).update();
		writeTags(id, request.tags());

		Map<String, Object> changes = new LinkedHashMap<>();
		if (before.price().compareTo(request.price()) != 0) {
			changes.put("price", Map.of("from", before.price(), "to", request.price()));
		}
		if (before.active() != request.active()) {
			changes.put("active", Map.of("from", before.active(), "to", request.active()));
		}
		if (!before.name().equals(request.name().trim())) {
			changes.put("name", Map.of("from", before.name(), "to", request.name().trim()));
		}
		if (!before.categoryId().equals(categoryId)) {
			changes.put("category", request.categorySlug());
		}
		changes.put("sku", sku);
		audit.record("PRODUCT_UPDATED", ENTITY, id, changes);
		return get(id);
	}

	@Transactional
	public AdminProduct setStock(Long id, int quantity) {
		current(id);
		int before = inventory.availableQuantity(id);
		inventory.setStock(id, quantity);
		jdbc.sql("UPDATE products SET updated_at = now() WHERE id = :id").param("id", id).update();
		audit.record("STOCK_SET", ENTITY, id, Map.of("from", before, "to", quantity));
		return get(id);
	}

	@Transactional
	public AdminProduct setActive(Long id, boolean active) {
		Current before = current(id);
		if (before.active() != active) {
			jdbc.sql("""
					UPDATE products SET status = :status, updated_at = now(), version = version + 1 WHERE id = :id
					""").param("status", active ? "ACTIVE" : "INACTIVE").param("id", id).update();
			audit.record(active ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED", ENTITY, id, Map.of("sku", before.sku()));
		}
		return get(id);
	}

	// ---------------------------------------------------------------- attributes

	/**
	 * Validates attribute values against the category's definitions and converts them to typed values.
	 * Keys with no values are treated as "not set".
	 */
	private Map<Definition, List<Object>> validateAttributes(Long categoryId, Map<String, List<String>> input) {
		Map<String, Definition> definitions = new HashMap<>();
		jdbc.sql("SELECT id, key, data_type FROM attribute_definitions WHERE category_id = :id")
			.param("id", categoryId)
			.query((rs, n) -> new Definition(rs.getLong("id"), rs.getString("key"),
					AttributeType.valueOf(rs.getString("data_type"))))
			.list()
			.forEach(d -> definitions.put(d.key(), d));
		Map<Definition, List<Object>> result = new LinkedHashMap<>();
		if (input == null) {
			return result;
		}
		input.forEach((key, rawValues) -> {
			List<String> values = rawValues == null ? List.of()
					: rawValues.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
			if (values.isEmpty()) {
				return;
			}
			Definition definition = definitions.get(key);
			if (definition == null) {
				throw ApiException.badRequest("UNKNOWN_ATTRIBUTE",
						"'" + key + "' is not an attribute of this category. Add it to the category first.")
					.with("attribute", key);
			}
			if (definition.type() != AttributeType.LIST && values.size() > 1) {
				throw invalidAttribute(key, "takes a single value.");
			}
			List<Object> typed = new ArrayList<>();
			for (String value : values) {
				if (value.length() > 200) {
					throw invalidAttribute(key, "values must be at most 200 characters.");
				}
				typed.add(switch (definition.type()) {
					case NUMBER -> parseNumber(key, value);
					case BOOL -> parseBool(key, value);
					case TEXT, LIST -> value;
				});
			}
			result.put(definition, typed);
		});
		return result;
	}

	private void writeAttributes(Long productId, Map<Definition, List<Object>> attributes) {
		attributes.forEach((definition, values) -> values.forEach(value -> jdbc.sql("""
				INSERT INTO product_attributes (product_id, attribute_id, value_text, value_number, value_bool)
				VALUES (:productId, :attributeId, :text, :number, :bool)
				""")
			.param("productId", productId)
			.param("attributeId", definition.id())
			.param("text", value instanceof String s ? s : null, java.sql.Types.VARCHAR)
			.param("number", value instanceof BigDecimal b ? b : null, java.sql.Types.NUMERIC)
			.param("bool", value instanceof Boolean b ? b : null, java.sql.Types.BOOLEAN)
			.update()));
	}

	private void writeTags(Long productId, List<String> tags) {
		if (tags == null) {
			return;
		}
		new LinkedHashSet<>(tags).forEach(tag -> jdbc.sql("INSERT INTO product_tags (product_id, tag) VALUES (:id, :tag)")
			.param("id", productId)
			.param("tag", tag)
			.update());
	}

	private static BigDecimal parseNumber(String key, String value) {
		try {
			BigDecimal number = new BigDecimal(value);
			if (number.abs().compareTo(new BigDecimal("999999999")) > 0) {
				throw invalidAttribute(key, "is out of range.");
			}
			return number;
		}
		catch (NumberFormatException ex) {
			throw invalidAttribute(key, "must be a number.");
		}
	}

	private static Boolean parseBool(String key, String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "true", "yes" -> Boolean.TRUE;
			case "false", "no" -> Boolean.FALSE;
			default -> throw invalidAttribute(key, "must be true or false.");
		};
	}

	private static ApiException invalidAttribute(String key, String problem) {
		return ApiException.badRequest("INVALID_ATTRIBUTE", "Attribute '" + key + "' " + problem).with("attribute", key);
	}

	// ---------------------------------------------------------------- helpers

	private Current current(Long id) {
		return jdbc.sql("SELECT id, sku, name, price, status, category_id, version FROM products WHERE id = :id")
			.param("id", id)
			.query((rs, n) -> new Current(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
					rs.getBigDecimal("price"), "ACTIVE".equals(rs.getString("status")), rs.getLong("category_id"),
					rs.getLong("version")))
			.optional()
			.orElseThrow(ProductAdminService::notFound);
	}

	private Long leafCategoryId(String slug) {
		Optional<Long> id = jdbc.sql("""
				SELECT c.id FROM categories c
				WHERE c.slug = :slug AND NOT EXISTS (SELECT 1 FROM categories ch WHERE ch.parent_id = c.id)
				""").param("slug", slug).query(Long.class).optional();
		return id.orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY",
				"Products must belong to an existing category (not a department).").with("field", "categorySlug"));
	}

	private String uniqueSlug(String name, String sku) {
		String base = slugify(name);
		boolean taken = jdbc.sql("SELECT count(*) FROM products WHERE slug = :slug")
			.param("slug", base)
			.query(Long.class)
			.single() > 0;
		return taken ? base + "-" + slugify(sku) : base;
	}

	static String slugify(String text) {
		String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		if (slug.isEmpty()) {
			slug = "product";
		}
		return slug.length() > 120 ? slug.substring(0, 120).replaceAll("-$", "") : slug;
	}

	private List<String> tags(Long id) {
		return jdbc.sql("SELECT tag FROM product_tags WHERE product_id = :id ORDER BY tag")
			.param("id", id)
			.query(String.class)
			.list();
	}

	private Map<String, List<String>> attributes(Long id) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		jdbc.sql("""
				SELECT ad.key, COALESCE(pa.value_text, pa.value_bool::text, trim_scale(pa.value_number)::text) AS value
				FROM product_attributes pa JOIN attribute_definitions ad ON ad.id = pa.attribute_id
				WHERE pa.product_id = :id ORDER BY ad.sort_order, pa.id
				""").param("id", id).query(rs -> {
			result.computeIfAbsent(rs.getString("key"), k -> new ArrayList<>()).add(rs.getString("value"));
		});
		return result;
	}

	private AdminProductRow row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		return new AdminProductRow(rs.getLong("id"), rs.getString("sku"), rs.getString("slug"), rs.getString("name"),
				rs.getString("brand"), rs.getBigDecimal("price"), "ACTIVE".equals(rs.getString("status")),
				rs.getInt("quality_tier"), rs.getBigDecimal("rating_avg"), rs.getInt("rating_count"),
				rs.getString("category_slug"), rs.getString("category_name"), 0,
				rs.getTimestamp("updated_at").toInstant());
	}

	private List<AdminProductRow> withStock(List<AdminProductRow> rows) {
		Map<Long, Integer> stock = inventory.availableQuantities(rows.stream().map(AdminProductRow::id).toList());
		return rows.stream()
			.map(r -> new AdminProductRow(r.id(), r.sku(), r.slug(), r.name(), r.brand(), r.price(), r.active(),
					r.qualityTier(), r.ratingAvg(), r.ratingCount(), r.categorySlug(), r.categoryName(),
					stock.getOrDefault(r.id(), 0), r.updatedAt()))
			.toList();
	}

	private static ApiException notFound() {
		return ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist.");
	}
}
