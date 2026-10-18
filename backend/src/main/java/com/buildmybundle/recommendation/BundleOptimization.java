package com.buildmybundle.recommendation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.buildmybundle.bundles.BundleModels.BundleTier;
import com.buildmybundle.bundles.BundleModels.BundleView;
import com.buildmybundle.bundles.BundleModels.EditableBundle;
import com.buildmybundle.bundles.BundleModels.SlotChoice;
import com.buildmybundle.bundles.BundleService;
import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityModels.Space;
import com.buildmybundle.compatibility.CompatibilityService;
import com.buildmybundle.goals.GoalModels.GoalSnapshot;
import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.GoalService;
import com.buildmybundle.recommendation.engine.BundleOptimizer.Limits;
import com.buildmybundle.recommendation.engine.EngineModels.Need;
import com.buildmybundle.recommendation.engine.EngineModels.OwnedCategory;
import com.buildmybundle.recommendation.engine.EngineModels.Problem;
import com.buildmybundle.recommendation.engine.EngineModels.ScoringContext;
import com.buildmybundle.recommendation.engine.EngineModels.Tier;
import com.buildmybundle.recommendation.engine.RecommendationEngine;
import com.buildmybundle.recommendation.engine.RecommendationEngine.TierOutcome;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Re-optimize around my locked items": runs the optimizer for the bundle's tier on the bundle's own
 * categories and budget, keeping locked products, and writes the new choices back into the bundle.
 */
@Service
public class BundleOptimization {

	private final BundleService bundles;
	private final GoalService goals;
	private final ProductCatalog catalog;
	private final CompatibilityService compatibility;

	public BundleOptimization(BundleService bundles, GoalService goals, ProductCatalog catalog,
			CompatibilityService compatibility) {
		this.bundles = bundles;
		this.goals = goals;
		this.catalog = catalog;
		this.compatibility = compatibility;
	}

	@Transactional
	/** @param strategy BUDGET, BALANCED or PREMIUM; null keeps the bundle's own tier */
	public BundleView reoptimize(UUID bundleId, Long userId, Tier strategy) {
		EditableBundle bundle = bundles.loadForEdit(bundleId, userId);
		GoalSnapshot goal = goals.find(bundle.goalId())
			.orElseThrow(() -> ApiException.notFound("GOAL_NOT_FOUND", "This goal does not exist."));
		Map<Long, CategoryInfo> categories = catalog.categories()
			.stream()
			.collect(Collectors.toMap(CategoryInfo::id, Function.identity()));

		// keep the bundle's composition: every slot is filled, locked slots keep their product
		List<Need> needs = bundle.slots()
			.stream()
			.map(s -> new Need(s.categoryId(), categories.get(s.categoryId()).slug(), categories.get(s.categoryId()).name(),
					true, s.quantity()))
			.toList();
		Map<String, Long> locked = new LinkedHashMap<>();
		bundle.slots().stream().filter(EditableBundle.Slot::locked)
			.forEach(s -> locked.put(categories.get(s.categoryId()).slug(), s.productId()));
		List<OwnedCategory> owned = goal.requirements()
			.stream()
			.filter(r -> r.kind() == RequirementKind.OWNED)
			.map(r -> new OwnedCategory(r.categorySlug(), categories.get(r.categoryId()).name()))
			.toList();
		Map<String, List<ProductFacts>> candidates = OutfitCandidates.load(catalog,
				needs.stream().map(Need::categoryId).toList(), categories, goal.fit(), java.util.Set.copyOf(locked.values()));
		Problem problem = new Problem(needs, candidates, compatibility.activeRules(),
				bundle.budget().movePointRight(2).longValueExact(), new Space(goal.spaceWidthCm(), goal.spaceDepthCm()),
				owned, OutfitCandidates.context(goal),
				categories.values().stream().collect(Collectors.toMap(CategoryInfo::slug, CategoryInfo::name)));

		Tier tier = strategy != null ? strategy
				: bundle.tier() == BundleTier.CUSTOM ? Tier.BALANCED : Tier.valueOf(bundle.tier().name());
		TierOutcome outcome = RecommendationEngine.optimizeTier(problem, tier, locked, Limits.DEFAULT);
		if (outcome.bundle() == null) {
			ApiException error = ApiException.conflict("REOPTIMIZE_INFEASIBLE", outcome.infeasibility().reasons().getFirst())
				.with("reasons", outcome.infeasibility().reasons())
				.with("suggestions", outcome.infeasibility().suggestions());
			if (outcome.infeasibility().minimumFeasibleCents() != null) {
				error.with("minimumFeasibleCost", BigDecimal.valueOf(outcome.infeasibility().minimumFeasibleCents(), 2));
			}
			throw error;
		}
		List<SlotChoice> choices = outcome.bundle()
			.solution()
			.chosen()
			.stream()
			.map(p -> new SlotChoice(p.need().categoryId(), p.product().id(), p.product().product().price(),
					outcome.bundle().explanations().get(p.product().id())))
			.toList();
		return bundles.applyChoices(bundleId, userId, choices, outcome.bundle().summary());
	}
}
