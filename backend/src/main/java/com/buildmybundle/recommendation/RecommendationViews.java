package com.buildmybundle.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.buildmybundle.bundles.BundleModels.BundleView;

/** API representation of a recommendation run. */
public final class RecommendationViews {

	private RecommendationViews() {
	}

	/**
	 * @param status OK when at least one bundle was found, INFEASIBLE otherwise
	 * @param preferredTier the tier matching the goal's chosen approach, highlighted in the UI
	 * @param notes caveats (identical tiers, owned equipment to double-check, early stop)
	 * @param reasons why no bundle fits (INFEASIBLE only)
	 * @param suggestions how to make the goal feasible (INFEASIBLE only)
	 * @param minimumFeasibleCost cheapest compatible cost of the must-haves when the budget is too low
	 */
	public record RunView(UUID id, UUID goalId, String goalTitle, String status, String preferredTier,
			BigDecimal budget, List<BundleView> bundles, List<String> notes, List<String> reasons,
			List<String> suggestions, BigDecimal minimumFeasibleCost, Stats stats, Instant createdAt) {
	}

	/** @param complete false if the search hit its time or node limit */
	public record Stats(String algorithmVersion, long nodesExplored, int durationMs, boolean complete) {
	}
}
