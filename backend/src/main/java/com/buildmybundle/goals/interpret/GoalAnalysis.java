package com.buildmybundle.goals.interpret;

import java.math.BigDecimal;
import java.util.List;

import com.buildmybundle.goals.GoalModels.Fit;
import com.buildmybundle.goals.GoalModels.Quality;
import com.buildmybundle.goals.GoalModels.RequirementKind;

/**
 * Structured requirements extracted from a free-text goal, used to pre-fill the goal wizard. Nothing here
 * is a recommendation: it only names categories, a budget and preferences that the customer confirms.
 *
 * @param source which interpreter produced it (RULES, or AI in phase 6)
 * @param matchedTerms the phrases that were recognised, shown to the customer for transparency
 * @param clarifications questions to ask when essential information is missing
 * @param fit wardrobe, sizes and colors stated in the text (never guessed; only the catalog's color names)
 */
public record GoalAnalysis(String source, String templateSlug, String title, BigDecimal budget, Quality quality,
		List<Item> items, List<String> usageTags, List<String> preferredBrands, List<String> matchedTerms,
		List<String> clarifications, Fit fit) {

	public record Item(String categorySlug, RequirementKind kind, int quantity) {
	}
}
