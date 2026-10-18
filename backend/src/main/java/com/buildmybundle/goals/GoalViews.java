package com.buildmybundle.goals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.buildmybundle.goals.GoalModels.Quality;
import com.buildmybundle.goals.GoalModels.RequirementKind;

/** API representations of goal templates and shopping goals. */
public final class GoalViews {

	private GoalViews() {
	}

	/**
	 * A category as offered in the wizard, with live availability. Prices cover in-stock products only and
	 * are null when nothing is in stock.
	 */
	public record CategoryOption(String slug, String name, String departmentSlug, String departmentName,
			long inStockCount, BigDecimal minPrice, BigDecimal maxPrice, List<String> brands) {
	}

	public record UsageOption(String tag, String label) {
	}

	public record TemplateItem(CategoryOption category, boolean required, boolean selectedByDefault, int quantity,
			String note) {
	}

	/** @param essentialsMinimum cheapest in-stock price of all required items: "complete setups from $X" */
	public record TemplateSummary(String slug, String title, String tagline, BigDecimal defaultBudget,
			BigDecimal essentialsMinimum, List<String> categoryNames) {
	}

	public record TemplateDetail(String slug, String title, String tagline, String description,
			BigDecimal defaultBudget, BigDecimal essentialsMinimum, List<UsageOption> usageOptions,
			List<TemplateItem> items) {
	}

	public record TemplateRef(String slug, String title) {
	}

	public record GoalItem(CategoryOption category, RequirementKind kind, int quantity) {
	}

	public record Space(BigDecimal widthCm, BigDecimal depthCm) {
	}

	/**
	 * Lower bound computed from live catalog prices, before compatibility rules (phase 4) narrow the choice.
	 *
	 * @param essentialsMinimum cheapest cost of the REQUIRED items
	 * @param selectedMinimum cheapest cost of REQUIRED + OPTIONAL items
	 * @param unavailableCategories categories to buy that currently have nothing in stock
	 */
	public record Estimate(BigDecimal essentialsMinimum, BigDecimal selectedMinimum, boolean essentialsFit,
			boolean selectionFits, List<String> unavailableCategories) {
	}

	public record GoalView(UUID id, String title, TemplateRef template, String rawText, BigDecimal budget,
			Quality quality, List<GoalItem> items, List<String> preferredBrands, List<String> usageTags, Space space,
			String notes, Instant createdAt, Estimate estimate, com.buildmybundle.goals.GoalModels.Fit fit) {
	}

	public record GoalSummary(UUID id, String title, String templateSlug, BigDecimal budget, Quality quality,
			int itemCount, Instant createdAt) {
	}
}
