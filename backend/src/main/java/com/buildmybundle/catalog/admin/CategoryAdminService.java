package com.buildmybundle.catalog.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminCategory;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AttributeDefinition;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AttributeRequest;
import com.buildmybundle.catalog.admin.AdminCatalogModels.AttributeType;
import com.buildmybundle.catalog.admin.AdminCatalogModels.CategoryRequest;
import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categories and their attribute definitions. A new category or attribute is immediately usable by
 * products, filters, compatibility rules, goal templates and the goal interpreter, with no code change.
 */
@Service
public class CategoryAdminService {

	private final JdbcClient jdbc;
	private final AuditLog audit;

	public CategoryAdminService(JdbcClient jdbc, AuditLog audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<AdminCategory> list() {
		Map<Long, List<AttributeDefinition>> attributes = new HashMap<>();
		jdbc.sql("""
				SELECT category_id, key, label, data_type, unit, filterable
				FROM attribute_definitions ORDER BY category_id, sort_order, id
				""").query(rs -> {
			attributes.computeIfAbsent(rs.getLong("category_id"), k -> new ArrayList<>())
				.add(new AttributeDefinition(rs.getString("key"), rs.getString("label"),
						AttributeType.valueOf(rs.getString("data_type")), rs.getString("unit"), rs.getBoolean("filterable")));
		});
		return jdbc.sql("""
				SELECT c.id, c.slug, c.name, c.description, d.slug AS department_slug, d.name AS department_name,
				       NOT EXISTS (SELECT 1 FROM categories ch WHERE ch.parent_id = c.id) AS leaf,
				       (SELECT count(*) FROM products p WHERE p.category_id = c.id) AS product_count
				FROM categories c LEFT JOIN categories d ON d.id = c.parent_id
				WHERE c.active
				ORDER BY coalesce(d.sort_order, c.sort_order), c.parent_id IS NOT NULL, c.sort_order, c.name
				""")
			.query((rs, n) -> new AdminCategory(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
					rs.getString("description"), rs.getString("department_slug"), rs.getString("department_name"),
					rs.getBoolean("leaf"), rs.getLong("product_count"),
					attributes.getOrDefault(rs.getLong("id"), List.of())))
			.list();
	}

	@Transactional
	public AdminCategory create(CategoryRequest request) {
		Long departmentId = jdbc.sql("SELECT id FROM categories WHERE slug = :slug AND parent_id IS NULL AND active")
			.param("slug", request.departmentSlug())
			.query(Long.class)
			.optional()
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_DEPARTMENT", "Choose an existing department.")
				.with("field", "departmentSlug"));
		if (exists("SELECT count(*) FROM categories WHERE slug = :slug", Map.of("slug", request.slug()))) {
			throw ApiException.conflict("CATEGORY_SLUG_TAKEN", "A category with this slug already exists.")
				.with("field", "slug");
		}
		Long id = jdbc.sql("""
				INSERT INTO categories (parent_id, slug, name, description, sort_order)
				VALUES (:parent, :slug, :name, :description,
				        (SELECT coalesce(max(sort_order), 0) + 1 FROM categories WHERE parent_id = :parent))
				RETURNING id
				""")
			.param("parent", departmentId)
			.param("slug", request.slug())
			.param("name", request.name().trim())
			.param("description", request.description() == null ? null : request.description().trim(),
					java.sql.Types.VARCHAR)
			.query(Long.class)
			.single();
		audit.record("CATEGORY_CREATED", "CATEGORY", id, Map.of("slug", request.slug(), "department", request.departmentSlug()));
		return find(request.slug());
	}

	@Transactional
	public AdminCategory addAttribute(String categorySlug, AttributeRequest request) {
		Long categoryId = jdbc.sql("""
				SELECT c.id FROM categories c
				WHERE c.slug = :slug AND NOT EXISTS (SELECT 1 FROM categories ch WHERE ch.parent_id = c.id)
				""")
			.param("slug", categorySlug)
			.query(Long.class)
			.optional()
			.orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found."));
		if (exists("SELECT count(*) FROM attribute_definitions WHERE category_id = :id AND key = :key",
				Map.of("id", categoryId, "key", request.key()))) {
			throw ApiException.conflict("ATTRIBUTE_EXISTS", "This category already has an attribute '" + request.key() + "'.")
				.with("field", "key");
		}
		jdbc.sql("""
				INSERT INTO attribute_definitions (category_id, key, label, data_type, unit, filterable, sort_order)
				VALUES (:id, :key, :label, :type, :unit, :filterable,
				        (SELECT coalesce(max(sort_order), 0) + 1 FROM attribute_definitions WHERE category_id = :id))
				""")
			.param("id", categoryId)
			.param("key", request.key())
			.param("label", request.label().trim())
			.param("type", request.type().name())
			.param("unit", request.unit() == null || request.unit().isBlank() ? null : request.unit().trim(),
					java.sql.Types.VARCHAR)
			.param("filterable", Objects.requireNonNullElse(request.filterable(), Boolean.TRUE))
			.update();
		audit.record("ATTRIBUTE_ADDED", "CATEGORY", categoryId,
				Map.of("category", categorySlug, "key", request.key(), "type", request.type().name()));
		return find(categorySlug);
	}

	private AdminCategory find(String slug) {
		return list().stream().filter(c -> c.slug().equals(slug)).findFirst().orElseThrow();
	}

	private boolean exists(String sql, Map<String, ?> params) {
		return jdbc.sql(sql).paramSource(params).query(Long.class).single() > 0;
	}
}
