package com.buildmybundle.catalog.admin;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.buildmybundle.catalog.admin.AdminCatalogModels.AdminBrand;
import com.buildmybundle.catalog.admin.AdminCatalogModels.BrandRequest;
import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Brands are data: admins add, rename, feature or retire them without code changes. */
@Service
public class BrandAdminService {

	private static final String ENTITY = "BRAND";

	private final JdbcClient jdbc;
	private final AuditLog audit;

	public BrandAdminService(JdbcClient jdbc, AuditLog audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<AdminBrand> list() {
		return jdbc.sql("""
				SELECT b.id, b.slug, b.name, b.description, b.featured, b.active, count(p.id) AS product_count
				FROM brands b LEFT JOIN products p ON p.brand_id = b.id AND p.status = 'ACTIVE'
				GROUP BY b.id ORDER BY b.active DESC, b.sort_order, b.name
				""")
			.query((rs, n) -> new AdminBrand(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
					rs.getString("description"), rs.getBoolean("featured"), rs.getBoolean("active"),
					rs.getLong("product_count")))
			.list();
	}

	@Transactional
	public AdminBrand create(BrandRequest request) {
		String name = request.name().trim();
		String slug = request.slug() != null ? request.slug() : ProductAdminService.slugify(name);
		checkUnique(name, slug, null);
		Long id = jdbc.sql("""
				INSERT INTO brands (slug, name, description, featured, active, sort_order)
				VALUES (:slug, :name, :description, :featured, :active, (SELECT coalesce(max(sort_order), 0) + 1 FROM brands))
				RETURNING id
				""")
			.param("slug", slug)
			.param("name", name)
			.param("description", Objects.requireNonNullElse(request.description(), "").trim())
			.param("featured", Boolean.TRUE.equals(request.featured()))
			.param("active", !Boolean.FALSE.equals(request.active()))
			.query(Long.class)
			.single();
		audit.record("BRAND_CREATED", ENTITY, id, Map.of("name", name, "slug", slug));
		return find(id);
	}

	@Transactional
	public AdminBrand update(Long id, BrandRequest request) {
		AdminBrand before = find(id);
		String name = request.name().trim();
		String slug = request.slug() != null ? request.slug() : before.slug();
		checkUnique(name, slug, id);
		jdbc.sql("""
				UPDATE brands SET name = :name, slug = :slug, description = :description, featured = :featured,
				       active = :active, updated_at = now()
				WHERE id = :id
				""")
			.param("name", name)
			.param("slug", slug)
			.param("description", request.description() != null ? request.description().trim() : before.description())
			.param("featured", Objects.requireNonNullElse(request.featured(), before.featured()))
			.param("active", Objects.requireNonNullElse(request.active(), before.active()))
			.param("id", id)
			.update();
		if (!before.name().equals(name)) {
			// products keep a denormalised brand name for search and order snapshots
			jdbc.sql("UPDATE products SET brand = :name, updated_at = now() WHERE brand_id = :id")
				.param("name", name)
				.param("id", id)
				.update();
		}
		audit.record("BRAND_UPDATED", ENTITY, id, Map.of("name", name, "slug", slug));
		return find(id);
	}

	private void checkUnique(String name, String slug, Long exceptId) {
		Long except = exceptId == null ? -1L : exceptId;
		if (jdbc.sql("SELECT count(*) FROM brands WHERE lower(name) = lower(:name) AND id <> :id")
			.param("name", name).param("id", except).query(Long.class).single() > 0) {
			throw ApiException.conflict("BRAND_NAME_TAKEN", "A brand with this name already exists.").with("field", "name");
		}
		if (jdbc.sql("SELECT count(*) FROM brands WHERE slug = :slug AND id <> :id")
			.param("slug", slug).param("id", except).query(Long.class).single() > 0) {
			throw ApiException.conflict("BRAND_SLUG_TAKEN", "A brand with this slug already exists.").with("field", "slug");
		}
	}

	private AdminBrand find(Long id) {
		return list().stream()
			.filter(b -> b.id().equals(id))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("BRAND_NOT_FOUND", "This brand does not exist."));
	}
}
