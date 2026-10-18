package com.buildmybundle.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.buildmybundle.bundles.OutfitFit;
import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.goals.GoalModels.Fit;
import com.buildmybundle.goals.GoalModels.GoalSnapshot;
import com.buildmybundle.recommendation.engine.EngineModels.ScoringContext;

/**
 * Candidates and scoring context for an outfit: only products the wearer can buy (their wardrobe, in stock in
 * their size), and products in one of their colors carry {@link ScoringContext#PREFERRED_COLOR_TAG} so the
 * engine's relevance score favors them.
 */
final class OutfitCandidates {

	private OutfitCandidates() {
	}

	/** @param keep products that stay candidates whatever the fit (locked items) */
	static Map<String, List<ProductFacts>> load(ProductCatalog catalog, List<Long> categoryIds,
			Map<Long, CategoryInfo> categories, Fit fit, Set<Long> keep) {
		return catalog.activeFactsInCategories(categoryIds)
			.stream()
			.filter(p -> keep.contains(p.id()) || OutfitFit.fits(p, fit, scale(categories, p)))
			.map(p -> OutfitFit.offersPreferredColor(p, fit, scale(categories, p)) ? withPreferredColor(p) : p)
			.collect(Collectors.groupingBy(ProductFacts::categorySlug, LinkedHashMap::new, Collectors.toList()));
	}

	static ScoringContext context(GoalSnapshot goal) {
		List<String> usage = new ArrayList<>(goal.usageTags());
		if (!goal.fit().colors().isEmpty()) {
			usage.add(ScoringContext.PREFERRED_COLOR_TAG);
		}
		return new ScoringContext(goal.contextTag(), List.copyOf(usage), goal.preferredBrands());
	}

	private static String scale(Map<Long, CategoryInfo> categories, ProductFacts p) {
		CategoryInfo category = categories.get(p.categoryId());
		return category == null ? null : category.sizeScale();
	}

	private static ProductFacts withPreferredColor(ProductFacts p) {
		Set<String> tags = new LinkedHashSet<>(p.tags());
		tags.add(ScoringContext.PREFERRED_COLOR_TAG);
		return new ProductFacts(p.product(), p.categoryId(), p.attributes(), Set.copyOf(tags));
	}
}
