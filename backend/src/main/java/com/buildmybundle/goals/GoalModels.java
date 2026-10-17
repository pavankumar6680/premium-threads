package com.buildmybundle.goals;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public types of the goals module (used by the web layer and by the recommendation engine). */
public final class GoalModels {

	private GoalModels() {
	}

	/** How much a customer wants to spend relative to the budget. */
	public enum Quality {
		BUDGET, BALANCED, PREMIUM
	}

	/** REQUIRED and OPTIONAL categories are bought; OWNED ones are excluded but still describe the setup. */
	public enum RequirementKind {
		REQUIRED, OPTIONAL, OWNED
	}

	public record CreateGoal(String templateSlug, String title, String rawText, BigDecimal budget, Quality quality,
			List<Item> items, List<String> preferredBrands, List<String> usageTags, BigDecimal spaceWidthCm,
			BigDecimal spaceDepthCm, String notes) {

		public record Item(String categorySlug, RequirementKind kind, int quantity) {
		}
	}

	/**
	 * Read-only view of a saved goal for other modules.
	 *
	 * @param contextTag product tag for the goal's purpose (from its template), or null for custom goals
	 */
	public record GoalSnapshot(UUID id, Long userId, String templateSlug, String contextTag, String title, BigDecimal budget,
			Quality quality, List<Requirement> requirements, List<String> preferredBrands, List<String> usageTags,
			BigDecimal spaceWidthCm, BigDecimal spaceDepthCm) {

		public record Requirement(Long categoryId, String categorySlug, RequirementKind kind, int quantity) {
		}
	}
}
