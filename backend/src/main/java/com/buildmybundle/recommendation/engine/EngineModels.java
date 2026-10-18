package com.buildmybundle.recommendation.engine;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Space;

/** Inputs and outputs of the recommendation engine. All money is in integer cents. */
public final class EngineModels {

	private EngineModels() {
	}

	/** A category to buy: required ones must be filled, optional ones may stay empty. */
	public record Need(Long categoryId, String categorySlug, String categoryName, boolean required, int quantity) {
	}

	public record OwnedCategory(String slug, String name) {
	}

	/** What a product is scored against: the goal's purpose, usage and brand preferences. */
	public record ScoringContext(String contextTag, List<String> usageTags, List<String> preferredBrands) {

		/** Synthetic tag of products that come in one of the shopper's colors, in their size. */
		public static final String PREFERRED_COLOR_TAG = "in-your-colors";
	}

	/**
	 * @param candidates active products per category slug, with live stock
	 * @param categoryNames slug → display name for every category mentioned by rules (for messages)
	 */
	public record Problem(List<Need> needs, Map<String, List<ProductFacts>> candidates, List<Rule> rules,
			long budgetCents, Space space, List<OwnedCategory> owned, ScoringContext context,
			Map<String, String> categoryNames) {
	}

	/**
	 * The three recommendation strategies. Each scores an option as
	 * {@code needWeight × (q·quality + r·relevance + b·brand) − costPenalty × cost / budget}.
	 */
	public enum Tier {

		/** Essentials only, strongly price-sensitive. */
		BUDGET(0.30, 0.50, 0.20, 4.0, "Budget bundle"),
		/**
		 * Value for money: quality and fit weighed against price, nice-to-haves when worth it. The penalty is
		 * calibrated so balanced bundles typically land mid-budget, leaving room for a distinct premium option.
		 */
		BALANCED(0.45, 0.40, 0.15, 1.2, "Balanced bundle"),
		/** Best quality that fits the budget; price only matters as the budget limit. */
		PREMIUM(0.70, 0.25, 0.05, 0.0, "Premium bundle");

		final double qualityWeight;
		final double relevanceWeight;
		final double brandWeight;
		final double costPenalty;
		public final String displayName;

		Tier(double qualityWeight, double relevanceWeight, double brandWeight, double costPenalty, String displayName) {
			this.qualityWeight = qualityWeight;
			this.relevanceWeight = relevanceWeight;
			this.brandWeight = brandWeight;
			this.costPenalty = costPenalty;
			this.displayName = displayName;
		}
	}

	/** One category's choice; {@code product} is null when an optional category was left out. */
	public record Pick(Need need, ProductFacts product) {

		public long costCents() {
			return product == null ? 0 : Scoring.cents(product) * need.quantity();
		}
	}

	public record Solution(List<Pick> picks, long costCents, double score) {

		public List<Pick> chosen() {
			return picks.stream().filter(p -> p.product() != null).toList();
		}

		/** Identity of the bundle's contents, used to detect identical tiers. */
		public String contentKey() {
			return chosen().stream()
				.map(p -> p.product().id() + "x" + p.need().quantity())
				.sorted()
				.collect(Collectors.joining(","));
		}
	}

	/** @param complete false when the search hit its node or time limit (result is the best found) */
	public record SearchResult(Solution best, long nodes, boolean complete) {
	}

	/** @param explanations product id → why it was chosen */
	public record TierBundle(Tier tier, Solution solution, Map<Long, String> explanations, String summary,
			boolean contextRelaxed) {
	}

	public record Infeasibility(List<String> reasons, Long minimumFeasibleCents, List<String> suggestions) {
	}

	public record EngineResult(List<TierBundle> bundles, List<String> notes, Infeasibility infeasibility, long nodes,
			boolean complete, long durationMs) {
	}
}
