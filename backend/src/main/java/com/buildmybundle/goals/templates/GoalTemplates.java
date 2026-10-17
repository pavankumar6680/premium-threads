package com.buildmybundle.goals.templates;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Read access to goal templates (store configuration maintained by admins). */
@Repository
@Transactional(readOnly = true)
public class GoalTemplates {

	public record Template(Long id, String slug, String title, String tagline, String description,
			BigDecimal defaultBudget, String contextTag, List<Item> items, List<UsageOption> usageOptions,
			List<String> keywords) {
	}

	public record Item(Long categoryId, boolean required, boolean selectedByDefault, int quantity, String note) {
	}

	public record UsageOption(String tag, String label) {
	}

	private record Row(Long id, String slug, String title, String tagline, String description, BigDecimal budget,
			String contextTag) {
	}

	private final JdbcClient jdbc;

	public GoalTemplates(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Template> findAllActive() {
		List<Row> rows = jdbc.sql("""
				SELECT id, slug, title, tagline, description, default_budget, context_tag
				FROM goal_templates WHERE active ORDER BY sort_order, title
				""").query((rs, n) -> new Row(rs.getLong("id"), rs.getString("slug"), rs.getString("title"),
				rs.getString("tagline"), rs.getString("description"), rs.getBigDecimal("default_budget"),
				rs.getString("context_tag")))
			.list();
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> ids = rows.stream().map(Row::id).toList();
		Map<Long, List<Item>> items = jdbc.sql("""
				SELECT template_id, category_id, required, selected_by_default, quantity, note
				FROM goal_template_items WHERE template_id IN (:ids) ORDER BY template_id, sort_order, id
				""")
			.param("ids", ids)
			.query((rs, n) -> Map.entry(rs.getLong("template_id"), new Item(rs.getLong("category_id"),
					rs.getBoolean("required"), rs.getBoolean("selected_by_default"), rs.getInt("quantity"),
					rs.getString("note"))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		Map<Long, List<UsageOption>> usage = jdbc.sql("""
				SELECT template_id, tag, label FROM goal_template_usage_options
				WHERE template_id IN (:ids) ORDER BY template_id, sort_order, tag
				""")
			.param("ids", ids)
			.query((rs, n) -> Map.entry(rs.getLong("template_id"),
					new UsageOption(rs.getString("tag"), rs.getString("label"))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		Map<Long, List<String>> keywords = jdbc
			.sql("SELECT template_id, keyword FROM goal_template_keywords WHERE template_id IN (:ids) ORDER BY keyword")
			.param("ids", ids)
			.query((rs, n) -> Map.entry(rs.getLong("template_id"), rs.getString("keyword")))
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		return rows.stream()
			.map(r -> new Template(r.id(), r.slug(), r.title(), r.tagline(), r.description(), r.budget(), r.contextTag(),
					items.getOrDefault(r.id(), List.of()), usage.getOrDefault(r.id(), List.of()),
					keywords.getOrDefault(r.id(), List.of())))
			.toList();
	}

	public Optional<Template> findActiveBySlug(String slug) {
		return findAllActive().stream().filter(t -> t.slug().equals(slug)).findFirst();
	}

	public Optional<Template> findById(Long id) {
		return findAllActive().stream().filter(t -> t.id().equals(id)).findFirst();
	}
}
