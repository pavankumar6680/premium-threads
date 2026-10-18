package com.buildmybundle.recommendation.web;

import java.util.UUID;

import com.buildmybundle.auth.AuthenticatedUser;
import com.buildmybundle.bundles.BundleModels.BundleView;
import com.buildmybundle.recommendation.BundleOptimization;
import com.buildmybundle.recommendation.RecommendationService;
import com.buildmybundle.recommendation.RecommendationViews.RunView;
import com.buildmybundle.recommendation.RunExplainer;
import com.buildmybundle.recommendation.RunExplainer.Explanation;
import com.buildmybundle.recommendation.engine.EngineModels.Tier;
import com.buildmybundle.shared.error.ApiException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recommendations")
@RestController
@RequestMapping("/api/v1")
class RecommendationController {

	private final RecommendationService recommendations;
	private final BundleOptimization optimization;
	private final RunExplainer explainer;

	RecommendationController(RecommendationService recommendations, BundleOptimization optimization,
			RunExplainer explainer) {
		this.recommendations = recommendations;
		this.optimization = optimization;
		this.explainer = explainer;
	}

	@Operation(summary = "Generate Budget, Balanced and Premium bundles for a goal",
			description = "Runs the compatibility-aware budget optimizer on live catalog data and stores the result. "
					+ "With differentFrom (a previous run of the goal), that run's pieces are avoided where the catalog "
					+ "has alternatives, giving other combinations for the same budget.")
	@PostMapping("/goals/{goalId}/recommendations")
	@ResponseStatus(HttpStatus.CREATED)
	RunView generate(@PathVariable UUID goalId,
			@org.springframework.web.bind.annotation.RequestParam(required = false) UUID differentFrom,
			@AuthenticationPrincipal AuthenticatedUser user) {
		return recommendations.generate(goalId, user == null ? null : user.getId(), differentFrom);
	}

	@GetMapping("/goals/{goalId}/recommendations/latest")
	RunView latest(@PathVariable UUID goalId) {
		return recommendations.latestForGoal(goalId);
	}

	@GetMapping("/recommendations/{runId}")
	RunView get(@PathVariable UUID runId) {
		return recommendations.get(runId);
	}

	@Operation(summary = "A plain-language overview of the results written by the local AI model",
			description = "Grounded in the run's facts only; status is OK, UNAVAILABLE (AI off, unreachable or slow) "
					+ "or REJECTED (the answer mentioned numbers that are not in the results).")
	@PostMapping("/recommendations/{runId}/explanation")
	Explanation explain(@PathVariable UUID runId) {
		return explainer.explain(runId);
	}

	@Operation(summary = "Re-optimize your bundle around its locked items",
			description = "Optional strategy=BUDGET|BALANCED|PREMIUM (default: the bundle's tier). Keeps the bundle's categories and locked products and picks the best compatible products "
					+ "for the rest within the bundle's budget. 409 REOPTIMIZE_INFEASIBLE explains when nothing fits.")
	@PostMapping("/bundles/{bundleId}/reoptimize")
	BundleView reoptimize(@PathVariable UUID bundleId, @AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String strategy) {
		return optimization.reoptimize(bundleId, user.getId(), parseStrategy(strategy));
	}

	private static Tier parseStrategy(String strategy) {
		if (strategy == null || strategy.isBlank()) {
			return null;
		}
		try {
			return Tier.valueOf(strategy.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.badRequest("INVALID_STRATEGY", "strategy must be BUDGET, BALANCED or PREMIUM.");
		}
	}
}
