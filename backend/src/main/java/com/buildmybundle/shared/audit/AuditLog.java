package com.buildmybundle.shared.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.buildmybundle.shared.web.PageResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only record of administrative changes. Entries are written in the caller's transaction, so a
 * change and its audit entry are committed (or rolled back) together.
 */
@Component
public class AuditLog {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	public record Entry(Long id, String actorEmail, String action, String entityType, String entityId,
			String details, Instant createdAt) {
	}

	/** Implemented by the authenticated principal so the audit log knows who acted without depending on auth. */
	public interface Actor {

		Long getId();

		String getUsername();
	}

	private final JdbcClient jdbc;

	public AuditLog(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String action, String entityType, Object entityId, Map<String, ?> details) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		Long actorId = null;
		String actorEmail = "system";
		if (auth != null && auth.getPrincipal() instanceof Actor actor) {
			actorId = actor.getId();
			actorEmail = actor.getUsername();
		}
		jdbc.sql("""
				INSERT INTO admin_audit_log (actor_id, actor_email, action, entity_type, entity_id, details)
				VALUES (:actorId, :actorEmail, :action, :entityType, :entityId, CAST(:details AS jsonb))
				""")
			.param("actorId", actorId)
			.param("actorEmail", actorEmail)
			.param("action", action)
			.param("entityType", entityType)
			.param("entityId", String.valueOf(entityId))
			.param("details", JSON.writeValueAsString(details))
			.update();
	}

	@Transactional(readOnly = true)
	public PageResponse<Entry> recent(String entityType, int page, int size) {
		String where = entityType == null ? "" : "WHERE entity_type = :type";
		long total = jdbc.sql("SELECT count(*) FROM admin_audit_log " + where)
			.paramSource(entityType == null ? Map.of() : Map.of("type", entityType))
			.query(Long.class)
			.single();
		List<Entry> content = jdbc.sql("""
				SELECT id, actor_email, action, entity_type, entity_id, details::text AS details, created_at
				FROM admin_audit_log %s ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset
				""".formatted(where))
			.paramSource(entityType == null ? Map.of("limit", size, "offset", page * size)
					: Map.of("type", entityType, "limit", size, "offset", page * size))
			.query((rs, n) -> new Entry(rs.getLong("id"), rs.getString("actor_email"), rs.getString("action"),
					rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("details"),
					rs.getTimestamp("created_at").toInstant()))
			.list();
		return PageResponse.of(content, page, size, total);
	}
}
