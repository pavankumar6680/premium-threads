package com.buildmybundle.catalog.admin;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminProduct;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminProductRow;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminVariant;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AttributeType;
import com.buildmybundle.catalog.admin.AdminCatalogModels.ProductRequest;
import com.buildmybundle.catalog.admin.AdminCatalogModels.VariantInput;
import com.buildmybundle.inventory.InventoryService;
import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product administration: details, price and discounts, merchandising flags, styles and occasions,
 * variants (size × color) and their stock, and status. Every change is audited.
 *
 * <p>
 * Edits use optimistic locking on {@code products.version}: an update states the version it was based on
 * and is refused with 409 if someone else changed the product meanwhile. Attribute values are validated
 * against the category's attribute definitions, because filters and outfit-matching rules read them.
 * Variants are never deleted (orders reference them): leaving one out of an update deactivates it.
 */
@Service
public class ProductAdminService {

	public static final int LOW_STOCK_THRESHOLD = 5;
	private static final String ENTITY = "PRODUCT";
	private static final String DEFAULT_SIZE = "One size";
	private static final String DEFAULT_COLOR = "Standard";

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

	private record Current(Long id, String sku, String name, BigDecimal price, BigDecimal compareAtPrice,
			boolean active, Long categoryId, String gender, boolean featured, boolean newArrival, boolean trending,
			String material, String care, long version) {
	}

	private record VariantRow(Long id, Long productId, String sku, String size, String color, String colorHex,
			String colorFamily, boolean active) {
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
			where.append(" AND (c.slug = :category OR c.parent_id = (SELECT id FROM categories WHERE slug = :category))");
			params.put("category", category);
		}
		if (active != null) {
			where.append(" AND p.status = :status");
			params.put("status", active ? "ACTIVE" : "INACTIVE");
		}
		String from = " FROM products p JOIN categories c ON c.id = p.category_id";
		String select = """
				SELECT p.id, p.sku, p.slug, p.name, p.brand, p.price, p.compare_at_price, p.status, p.quality_tier,
				       p.rating_avg, p.rating_count, c.slug AS category_slug, c.name AS category_name, p.gender, p.featured,
				       p.updated_at""" + from + where;
		if (lowStock || sort == Sort.STOCK) {
			// stock belongs to the inventory module: totals are computed in memory over the filtered set
			List<AdminProductRow> all = withStock(jdbc.sql(select).paramSource(params).query(this::row).list());
			List<AdminProductRow> filtered = all.stream()
				.filter(r -> !lowStock || r.stock() <= LOW_STOCK_THRESHOLD)
				.sorted(sort == Sort.STOCK || lowStock
						? Comparator.comparingInt(AdminProductRow::stock).thenComparing(AdminProductRow::name)
						: Comparator.comparing(AdminProductRow::name))
				.toList();
			return PageResponse.of(filtered.stream().skip((long) page * size).limit(size).toList(), page, size,
					filtered.size());
		}
		long total = jdbc.sql("SELECT count(*)" + from + where).paramSource(params).query(Long.class).single();
		if (total == 0) {
			return PageResponse.of(List.of(), page, size, 0);
		}
		String order = switch (sort == null ? Sort.NAME : sort) {
			case PRICE -> " ORDER BY p.price, p.id";
			case UPDATED -> " ORDER BY p.updated_at DESC, p.id DESC";
			default -> " ORDER BY p.name, p.id";
		};
		params.put("limit", size);
		params.put("offset", page * size);
		List<AdminProductRow> rows = withStock(jdbc.sql(select + order + " LIMIT :limit OFFSET :offset")
			.paramSource(params)
			.query(this::row)
			.list());
		return PageResponse.of(rows, page, size, total);
	}

	@Transactional(readOnly = true)
	public AdminProduct get(Long id) {
		record Head(String sku, String slug, String name, String brand, String brandSlug, String description,
				BigDecimal price, BigDecimal compareAt, int tier, boolean active, String category, String gender,
				boolean featured, boolean newArrival, boolean trending, String material, String care,
				BigDecimal ratingAvg, int ratingCount, long version, java.time.Instant updatedAt) {
		}
		Head h = jdbc.sql("""
				SELECT p.sku, p.slug, p.name, p.brand, b.slug AS brand_slug, p.description, p.price, p.compare_at_price,
				       p.quality_tier, p.status, c.slug AS category_slug, p.gender, p.featured, p.new_arrival, p.trending,
				       p.material, p.care, p.rating_avg, p.rating_count, p.version, p.updated_at
				FROM products p JOIN categories c ON c.id = p.category_id JOIN brands b ON b.id = p.brand_id
				WHERE p.id = :id
				""").param("id", id).query((rs, n) -> new Head(rs.getString("sku"), rs.getString("slug"),
				rs.getString("name"), rs.getString("brand"), rs.getString("brand_slug"), rs.getString("description"),
				rs.getBigDecimal("price"), rs.getBigDecimal("compare_at_price"), rs.getInt("quality_tier"),
				"ACTIVE".equals(rs.getString("status")), rs.getString("category_slug"), rs.getString("gender"),
				rs.getBoolean("featured"), rs.getBoolean("new_arrival"), rs.getBoolean("trending"),
				rs.getString("material"), rs.getString("care"), rs.getBigDecimal("rating_avg"),
				rs.getInt("rating_count"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant()))
			.optional()
			.orElseThrow(ProductAdminService::notFound);
		List<VariantRow> variantRows = variants(List.of(id));
		Map<Long, Integer> stock = inventory.availableQuantities(variantRows.stream().map(VariantRow::id).toList());
		List<AdminVariant> variants = variantRows.stream()
			.map(v -> new AdminVariant(v.id(), v.sku(), v.size(), v.color(), v.colorHex(), v.colorFamily(), v.active(),
					stock.getOrDefault(v.id(), 0)))
			.toList();
		int total = variants.stream().filter(AdminVariant::active).mapToInt(AdminVariant::stock).sum();
		return new AdminProduct(id, h.sku(), h.slug(), h.name(), h.brand(), h.brandSlug(), h.description(), h.price(),
				h.compareAt(), h.tier(), h.active(), h.category(), h.gender(), h.featured(), h.newArrival(), h.trending(),
				h.material(), h.care(), total, variants, slugs("product_styles", "styles", "style_id", id),
				slugs("product_occasions", "occasions", "occasion_id", id), tags(id), attributes(id), h.ratingAvg(),
				h.ratingCount(), h.version(), h.updatedAt());
	}

	// ---------------------------------------------------------------- commands

	@Transactional
	public AdminProduct create(ProductRequest request) {
		Long categoryId = leafCategoryId(request.categorySlug());
		Brand brand = brand(request.brand());
		String sku = request.sku().trim().toUpperCase(Locale.ROOT);
		if (jdbc.sql("SELECT count(*) FROM products WHERE sku = :sku").param("sku", sku).query(Long.class).single() > 0) {
			throw ApiException.conflict("SKU_TAKEN", "Another product already uses SKU " + sku + ".").with("field", "sku");
		}
		checkCompareAt(request.price(), request.compareAtPrice());
		Map<Definition, List<Object>> attributes = validateAttributes(categoryId, request.attributes());
		List<Long> styleIds = vocabularyIds("styles", request.styles(), "styles");
		List<Long> occasionIds = vocabularyIds("occasions", request.occasions(), "occasions");
		List<VariantInput> variants = request.variants() == null || request.variants().isEmpty()
				? List.of(new VariantInput(null, DEFAULT_SIZE, DEFAULT_COLOR, null, "NEUTRAL", true,
						Objects.requireNonNullElse(request.stock(), 0)))
				: request.variants();
		checkDistinctOptions(variants);
		Long id = jdbc.sql("""
				INSERT INTO products (category_id, brand_id, brand, sku, slug, name, description, price, compare_at_price,
				                      quality_tier, status, gender, featured, new_arrival, trending, material, care)
				VALUES (:categoryId, :brandId, :brand, :sku, :slug, :name, :description, :price, :compareAt, :tier, :status,
				        :gender, :featured, :newArrival, :trending, :material, :care)
				RETURNING id
				""")
			.param("categoryId", categoryId)
			.param("brandId", brand.id())
			.param("brand", brand.name())
			.param("sku", sku)
			.param("slug", uniqueSlug(request.name(), sku))
			.param("name", request.name().trim())
			.param("description", Objects.requireNonNullElse(request.description(), "").trim())
			.param("price", request.price())
			.param("compareAt", request.compareAtPrice(), Types.NUMERIC)
			.param("tier", request.qualityTier())
			.param("status", request.active() ? "ACTIVE" : "INACTIVE")
			.param("gender", Objects.requireNonNullElse(request.gender(), "UNISEX"))
			.param("featured", Boolean.TRUE.equals(request.featured()))
			.param("newArrival", Boolean.TRUE.equals(request.newArrival()))
			.param("trending", Boolean.TRUE.equals(request.trending()))
			.param("material", blankToNull(request.material()), Types.VARCHAR)
			.param("care", blankToNull(request.care()), Types.VARCHAR)
			.query(Long.class)
			.single();
		writeAttributes(id, attributes);
		writeTags(id, request.tags());
		writeLinks("product_styles", "style_id", id, styleIds);
		writeLinks("product_occasions", "occasion_id", id, occasionIds);
		saveVariants(id, sku, variants);
		audit.record("PRODUCT_CREATED", ENTITY, id, Map.of("sku", sku, "name", request.name().trim(), "price",
				request.price(), "category", request.categorySlug(), "variants", variants.size()));
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
		Brand brand = brand(request.brand());
		String sku = request.sku().trim().toUpperCase(Locale.ROOT);
		if (jdbc.sql("SELECT count(*) FROM products WHERE sku = :sku AND id <> :id")
			.param("sku", sku)
			.param("id", id)
			.query(Long.class)
			.single() > 0) {
			throw ApiException.conflict("SKU_TAKEN", "Another product already uses SKU " + sku + ".").with("field", "sku");
		}
		// unspecified merchandising fields keep their current values
		BigDecimal compareAt = request.compareAtPrice() != null ? request.compareAtPrice()
				: before.compareAtPrice() != null && before.compareAtPrice().compareTo(request.price()) > 0
						? before.compareAtPrice() : null;
		checkCompareAt(request.price(), compareAt);
		Map<Definition, List<Object>> attributes = validateAttributes(categoryId, request.attributes());
		List<Long> styleIds = request.styles() == null ? null : vocabularyIds("styles", request.styles(), "styles");
		List<Long> occasionIds = request.occasions() == null ? null
				: vocabularyIds("occasions", request.occasions(), "occasions");
		if (request.variants() != null) {
			checkDistinctOptions(request.variants());
		}
		int updated = jdbc.sql("""
				UPDATE products SET category_id = :categoryId, brand_id = :brandId, brand = :brand, sku = :sku, name = :name,
				       description = :description, price = :price, compare_at_price = :compareAt, quality_tier = :tier,
				       status = :status, gender = :gender, featured = :featured, new_arrival = :newArrival,
				       trending = :trending, material = :material, care = :care, updated_at = now(), version = version + 1
				WHERE id = :id AND version = :version
				""")
			.param("categoryId", categoryId)
			.param("brandId", brand.id())
			.param("brand", brand.name())
			.param("sku", sku)
			.param("name", request.name().trim())
			.param("description", Objects.requireNonNullElse(request.description(), "").trim())
			.param("price", request.price())
			.param("compareAt", compareAt, Types.NUMERIC)
			.param("tier", request.qualityTier())
			.param("status", request.active() ? "ACTIVE" : "INACTIVE")
			.param("gender", Objects.requireNonNullElse(request.gender(), before.gender()))
			.param("featured", Objects.requireNonNullElse(request.featured(), before.featured()))
			.param("newArrival", Objects.requireNonNullElse(request.newArrival(), before.newArrival()))
			.param("trending", Objects.requireNonNullElse(request.trending(), before.trending()))
			.param("material", request.material() != null ? blankToNull(request.material()) : before.material(), Types.VARCHAR)
			.param("care", request.care() != null ? blankToNull(request.care()) : before.care(), Types.VARCHAR)
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
		if (styleIds != null) {
			jdbc.sql("DELETE FROM product_styles WHERE product_id = :id").param("id", id).update();
			writeLinks("product_styles", "style_id", id, styleIds);
		}
		if (occasionIds != null) {
			jdbc.sql("DELETE FROM product_occasions WHERE product_id = :id").param("id", id).update();
			writeLinks("product_occasions", "occasion_id", id, occasionIds);
		}
		if (request.variants() != null) {
			saveVariants(id, sku, request.variants());
		}

		Map<String, Object> changes = new LinkedHashMap<>();
		if (before.price().compareTo(request.price()) != 0) {
			changes.put("price", Map.of("from", before.price(), "to", request.price()));
		}
		if (!Objects.equals(before.compareAtPrice(), compareAt)) {
			changes.put("compareAtPrice", Map.of("from", String.valueOf(before.compareAtPrice()), "to", String.valueOf(compareAt)));
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
		if (request.variants() != null) {
			changes.put("variants", request.variants().size());
		}
		changes.put("sku", sku);
		audit.record("PRODUCT_UPDATED", ENTITY, id, changes);
		return get(id);
	}

	/** Sets the stock of a product sold in a single option; products with sizes or colors use {@link #setVariantStock}. */
	@Transactional
	public AdminProduct setStock(Long id, int quantity) {
		current(id);
		List<VariantRow> active = variants(List.of(id)).stream().filter(VariantRow::active).toList();
		if (active.size() != 1) {
			throw ApiException.conflict("VARIANT_REQUIRED", "This product has several sizes or colors: set stock per variant.")
				.with("variantIds", active.stream().map(VariantRow::id).toList());
		}
		setVariantStock(active.getFirst().id(), quantity);
		return get(id);
	}

	@Transactional
	public AdminProduct setVariantStock(Long variantId, int quantity) {
		VariantRow variant = variantsByIds(List.of(variantId)).stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("VARIANT_NOT_FOUND", "This variant does not exist."));
		int before = inventory.availableQuantity(variantId);
		inventory.setStock(variantId, quantity);
		jdbc.sql("UPDATE products SET updated_at = now() WHERE id = :id").param("id", variant.productId()).update();
		audit.record("STOCK_SET", ENTITY, variant.productId(),
				Map.of("variant", variant.sku(), "from", before, "to", quantity));
		return get(variant.productId());
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

	// ---------------------------------------------------------------- variants

	private void checkDistinctOptions(List<VariantInput> variants) {
		Set<String> seen = new HashSet<>();
		for (VariantInput v : variants) {
			String key = v.size().trim().toUpperCase(Locale.ROOT) + "|" + v.color().trim().toUpperCase(Locale.ROOT);
			if (!seen.add(key)) {
				throw ApiException.badRequest("DUPLICATE_VARIANT", "Size " + v.size() + " in " + v.color() + " is listed twice.")
					.with("field", "variants");
			}
		}
	}

	/** Upserts variants: existing ones (by id) are updated, new ones created, missing ones deactivated. */
	private void saveVariants(Long productId, String productSku, List<VariantInput> inputs) {
		Map<Long, VariantRow> existing = variants(List.of(productId)).stream()
			.collect(Collectors.toMap(VariantRow::id, v -> v));
		Set<Long> kept = new HashSet<>();
		int order = 0;
		int next = existing.size() + 1;
		for (VariantInput input : inputs) {
			String size = input.size().trim();
			String color = input.color().trim();
			String family = Objects.requireNonNullElse(input.colorFamily(), "NEUTRAL");
			boolean active = !Boolean.FALSE.equals(input.active());
			if (input.id() != null) {
				if (!existing.containsKey(input.id())) {
					throw ApiException.badRequest("VARIANT_NOT_FOUND", "Variant " + input.id() + " does not belong to this product.")
						.with("field", "variants");
				}
				jdbc.sql("""
						UPDATE product_variants SET size = :size, color = :color, color_hex = :hex, color_family = :family,
						       active = :active, sort_order = :order
						WHERE id = :id
						""").param("size", size).param("color", color).param("hex", blankToNull(input.colorHex()), Types.VARCHAR)
					.param("family", family).param("active", active).param("order", order++).param("id", input.id())
					.update();
				kept.add(input.id());
			}
			else {
				String variantSku = uniqueVariantSku(productSku, next++);
				Long variantId = jdbc.sql("""
						INSERT INTO product_variants (product_id, sku, size, color, color_hex, color_family, active, sort_order)
						VALUES (:productId, :sku, :size, :color, :hex, :family, :active, :order)
						RETURNING id
						""").param("productId", productId).param("sku", variantSku).param("size", size).param("color", color)
					.param("hex", blankToNull(input.colorHex()), Types.VARCHAR).param("family", family).param("active", active)
					.param("order", order++)
					.query(Long.class)
					.single();
				inventory.setStock(variantId, Objects.requireNonNullElse(input.stock(), 0));
				kept.add(variantId);
			}
		}
		existing.keySet().stream().filter(id -> !kept.contains(id)).forEach(id -> jdbc
			.sql("UPDATE product_variants SET active = FALSE WHERE id = :id")
			.param("id", id)
			.update());
	}

	private String uniqueVariantSku(String productSku, int start) {
		for (int n = start;; n++) {
			String candidate = productSku + "-V" + n;
			if (jdbc.sql("SELECT count(*) FROM product_variants WHERE sku = :sku").param("sku", candidate)
				.query(Long.class).single() == 0) {
				return candidate;
			}
		}
	}

	private List<VariantRow> variants(List<Long> productIds) {
		if (productIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT id, product_id, sku, size, color, color_hex, color_family, active FROM product_variants
				WHERE product_id IN (:ids) ORDER BY product_id, sort_order, id
				""").param("ids", productIds).query(this::variantRow).list();
	}

	private List<VariantRow> variantsByIds(List<Long> ids) {
		return jdbc.sql("""
				SELECT id, product_id, sku, size, color, color_hex, color_family, active FROM product_variants WHERE id IN (:ids)
				""").param("ids", ids).query(this::variantRow).list();
	}

	private VariantRow variantRow(ResultSet rs, int n) throws SQLException {
		return new VariantRow(rs.getLong("id"), rs.getLong("product_id"), rs.getString("sku"), rs.getString("size"),
				rs.getString("color"), rs.getString("color_hex"), rs.getString("color_family"), rs.getBoolean("active"));
	}

	// ---------------------------------------------------------------- brands, styles, occasions

	private record Brand(Long id, String name) {
	}

	private Brand brand(String nameOrSlug) {
		return jdbc.sql("SELECT id, name FROM brands WHERE active AND (lower(name) = lower(:v) OR slug = lower(:v))")
			.param("v", nameOrSlug.trim())
			.query((rs, n) -> new Brand(rs.getLong("id"), rs.getString("name")))
			.optional()
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_BRAND", "Choose an existing brand (add new brands first).")
				.with("field", "brand"));
	}

	private List<Long> vocabularyIds(String table, List<String> slugs, String field) {
		if (slugs == null || slugs.isEmpty()) {
			return List.of();
		}
		List<String> distinct = slugs.stream().map(String::trim).distinct().toList();
		Map<String, Long> found = new HashMap<>();
		jdbc.sql("SELECT id, slug FROM " + table + " WHERE slug IN (:slugs)").param("slugs", distinct).query(rs -> {
			found.put(rs.getString("slug"), rs.getLong("id"));
		});
		for (String slug : distinct) {
			if (!found.containsKey(slug)) {
				throw ApiException.badRequest("UNKNOWN_" + field.toUpperCase(Locale.ROOT).replaceAll("S$", ""),
						"Unknown " + field.replaceAll("s$", "") + ": " + slug).with("field", field);
			}
		}
		return distinct.stream().map(found::get).toList();
	}

	private void writeLinks(String table, String column, Long productId, List<Long> ids) {
		ids.forEach(linkId -> jdbc.sql("INSERT INTO " + table + " (product_id, " + column + ") VALUES (:p, :l)")
			.param("p", productId)
			.param("l", linkId)
			.update());
	}

	private List<String> slugs(String linkTable, String table, String column, Long productId) {
		return jdbc.sql("SELECT t.slug FROM " + linkTable + " l JOIN " + table + " t ON t.id = l." + column
				+ " WHERE l.product_id = :id ORDER BY t.sort_order").param("id", productId).query(String.class).list();
	}

	private static void checkCompareAt(BigDecimal price, BigDecimal compareAt) {
		if (compareAt != null && compareAt.compareTo(price) <= 0) {
			throw ApiException.badRequest("INVALID_DISCOUNT", "The original price must be higher than the current price.")
				.with("field", "compareAtPrice");
		}
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
			.param("text", value instanceof String s ? s : null, Types.VARCHAR)
			.param("number", value instanceof BigDecimal b ? b : null, Types.NUMERIC)
			.param("bool", value instanceof Boolean b ? b : null, Types.BOOLEAN)
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
		return jdbc.sql("""
				SELECT id, sku, name, price, compare_at_price, status, category_id, gender, featured, new_arrival, trending,
				       material, care, version
				FROM products WHERE id = :id
				""")
			.param("id", id)
			.query((rs, n) -> new Current(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
					rs.getBigDecimal("price"), rs.getBigDecimal("compare_at_price"), "ACTIVE".equals(rs.getString("status")),
					rs.getLong("category_id"), rs.getString("gender"), rs.getBoolean("featured"),
					rs.getBoolean("new_arrival"), rs.getBoolean("trending"), rs.getString("material"), rs.getString("care"),
					rs.getLong("version")))
			.optional()
			.orElseThrow(ProductAdminService::notFound);
	}

	private Long leafCategoryId(String slug) {
		return jdbc.sql("""
				SELECT c.id FROM categories c
				WHERE c.slug = :slug AND c.active AND NOT EXISTS (SELECT 1 FROM categories ch WHERE ch.parent_id = c.id)
				""").param("slug", slug).query(Long.class).optional()
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY",
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

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
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

	private AdminProductRow row(ResultSet rs, int n) throws SQLException {
		return new AdminProductRow(rs.getLong("id"), rs.getString("sku"), rs.getString("slug"), rs.getString("name"),
				rs.getString("brand"), rs.getBigDecimal("price"), rs.getBigDecimal("compare_at_price"),
				"ACTIVE".equals(rs.getString("status")), rs.getInt("quality_tier"), rs.getBigDecimal("rating_avg"),
				rs.getInt("rating_count"), rs.getString("category_slug"), rs.getString("category_name"),
				rs.getString("gender"), rs.getBoolean("featured"), 0, 0, rs.getTimestamp("updated_at").toInstant());
	}

	/** Totals over active variants, and the number of active variants. */
	private List<AdminProductRow> withStock(List<AdminProductRow> rows) {
		List<VariantRow> variants = variants(rows.stream().map(AdminProductRow::id).toList()).stream()
			.filter(VariantRow::active)
			.toList();
		Map<Long, Integer> stock = inventory.availableQuantities(variants.stream().map(VariantRow::id).toList());
		Map<Long, Integer> totals = variants.stream()
			.collect(Collectors.groupingBy(VariantRow::productId, Collectors.summingInt(v -> stock.getOrDefault(v.id(), 0))));
		Map<Long, Long> counts = variants.stream().collect(Collectors.groupingBy(VariantRow::productId, Collectors.counting()));
		return rows.stream()
			.map(r -> new AdminProductRow(r.id(), r.sku(), r.slug(), r.name(), r.brand(), r.price(), r.compareAtPrice(),
					r.active(), r.qualityTier(), r.ratingAvg(), r.ratingCount(), r.categorySlug(), r.categoryName(),
					r.gender(), r.featured(), totals.getOrDefault(r.id(), 0), counts.getOrDefault(r.id(), 0L).intValue(),
					r.updatedAt()))
			.toList();
	}

	private static ApiException notFound() {
		return ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist.");
	}
}
