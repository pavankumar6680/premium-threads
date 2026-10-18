package com.buildmybundle.recommendation;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.buildmybundle.bundles.BundleModels.BundleTier;
import com.buildmybundle.bundles.BundleModels.NewBundle;
import com.buildmybundle.bundles.BundleModels.NewItem;
import com.buildmybundle.bundles.BundleService;
import com.buildmybundle.bundles.OutfitFit;
import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.catalog.VariantSummary;
import com.buildmybundle.compatibility.CompatibilityModels.Space;
import com.buildmybundle.compatibility.CompatibilityService;
import com.buildmybundle.goals.GoalModels.Fit;
import com.buildmybundle.goals.GoalModels.GoalSnapshot;
import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.GoalService;
import com.buildmybundle.recommendation.RecommendationViews.RunView;
import com.buildmybundle.recommendation.RecommendationViews.Stats;
import com.buildmybundle.recommendation.engine.BundleOptimizer.Limits;
import com.buildmybundle.recommendation.engine.EngineModels.EngineResult;
import com.buildmybundle.recommendation.engine.EngineModels.Need;
import com.buildmybundle.recommendation.engine.EngineModels.OwnedCategory;
import com.buildmybundle.recommendation.engine.EngineModels.Pick;
import com.buildmybundle.recommendation.engine.EngineModels.Problem;
import com.buildmybundle.recommendation.engine.EngineModels.ScoringContext;
import com.buildmybundle.recommendation.engine.EngineModels.TierBundle;
import com.buildmybundle.recommendation.engine.RecommendationEngine;
import com.buildmybundle.shared.error.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a saved goal into a recommendation run: loads the goal, live candidates and active rules, runs the
 * engine, and stores the run with its bundles so the result can be revisited and shared.
 */
@Service
public class RecommendationService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

	private final GoalService goals;
	private final ProductCatalog catalog;
	private final CompatibilityService compatibility;
	private final BundleService bundles;
	private final JdbcClient jdbc;

	public RecommendationService(GoalService goals, ProductCatalog catalog, CompatibilityService compatibility,
			BundleService bundles, JdbcClient jdbc) {
		this.goals = goals;
		this.catalog = catalog;
		this.compatibility = compatibility;
		this.bundles = bundles;
		this.jdbc = jdbc;
	}

	@Transactional
	public RunView generate(UUID goalId, Long userId) {
		GoalSnapshot goal = goals.find(goalId)
			.orElseThrow(() -> ApiException.notFound("GOAL_NOT_FOUND", "This goal does not exist."));
		Problem problem = problem(goal);
		EngineResult result = RecommendationEngine.recommend(problem, Limits.DEFAULT);

		UUID runId = UUID.randomUUID();
		boolean feasible = !result.bundles().isEmpty();
		Long minCents = result.infeasibility() == null ? null : result.infeasibility().minimumFeasibleCents();
		jdbc.sql("""
				INSERT INTO recommendation_runs (id, goal_id, algorithm_version, status, budget, min_feasible_cost,
				                                 nodes_explored, duration_ms, complete)
				VALUES (:id, :goalId, :version, :status, :budget, :minCost, :nodes, :duration, :complete)
				""")
			.param("id", runId)
			.param("goalId", goalId)
			.param("version", RecommendationEngine.ALGORITHM_VERSION)
			.param("status", feasible ? "OK" : "INFEASIBLE")
			.param("budget", goal.budget())
			.param("minCost", minCents == null ? null : BigDecimal.valueOf(minCents, 2))
			.param("nodes", result.nodes())
			.param("duration", (int) result.durationMs())
			.param("complete", result.complete())
			.update();
		saveMessages(runId, "NOTE", result.notes());
		if (result.infeasibility() != null) {
			saveMessages(runId, "REASON", result.infeasibility().reasons());
			saveMessages(runId, "SUGGESTION", result.infeasibility().suggestions());
		}
		for (TierBundle bundle : result.bundles()) {
			List<NewItem> items = items(bundle, goal.fit());
			bundles.createRecommended(new NewBundle(goalId, runId, userId, BundleTier.valueOf(bundle.tier().name()),
					bundle.tier().displayName, goal.budget(), BigDecimal.valueOf(bundle.solution().costCents(), 2),
					BigDecimal.valueOf(bundle.solution().score()).setScale(4, java.math.RoundingMode.HALF_UP),
					summary(bundle, items, catalog), items));
		}
		log.info("Recommendation run {} for goal {}: {} bundle(s), {} nodes in {} ms", runId, goalId,
				result.bundles().size(), result.nodes(), result.durationMs());
		return get(runId);
	}

	@Transactional(readOnly = true)
	public RunView get(UUID runId) {
		record Row(UUID id, UUID goalId, String status, BigDecimal budget, BigDecimal minCost, String version,
				long nodes, int duration, boolean complete, Timestamp createdAt) {
		}
		Row row = jdbc.sql("SELECT * FROM recommendation_runs WHERE id = :id")
			.param("id", runId)
			.query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("goal_id", UUID.class),
					rs.getString("status"), rs.getBigDecimal("budget"), rs.getBigDecimal("min_feasible_cost"),
					rs.getString("algorithm_version"), rs.getLong("nodes_explored"), rs.getInt("duration_ms"),
					rs.getBoolean("complete"), rs.getTimestamp("created_at")))
			.optional()
			.orElseThrow(() -> ApiException.notFound("RECOMMENDATION_NOT_FOUND", "These recommendations do not exist."));
		Map<String, List<String>> messages = new LinkedHashMap<>();
		jdbc.sql("SELECT kind, message FROM recommendation_run_messages WHERE run_id = :id ORDER BY position, id")
			.param("id", runId)
			.query(rs -> {
				messages.computeIfAbsent(rs.getString("kind"), k -> new ArrayList<>()).add(rs.getString("message"));
			});
		GoalSnapshot goal = goals.find(row.goalId()).orElseThrow();
		return new RunView(row.id(), row.goalId(), goal.title(), row.status(), goal.quality().name(), row.budget(),
				bundles.forRun(runId), messages.getOrDefault("NOTE", List.of()), messages.getOrDefault("REASON", List.of()),
				messages.getOrDefault("SUGGESTION", List.of()), row.minCost(),
				new Stats(row.version(), row.nodes(), row.duration(), row.complete()), row.createdAt().toInstant());
	}

	@Transactional(readOnly = true)
	public RunView latestForGoal(UUID goalId) {
		UUID runId = jdbc
			.sql("SELECT id FROM recommendation_runs WHERE goal_id = :goalId ORDER BY created_at DESC LIMIT 1")
			.param("goalId", goalId)
			.query(UUID.class)
			.optional()
			.orElseThrow(() -> ApiException.notFound("NO_RECOMMENDATIONS", "No recommendations for this goal yet."));
		return get(runId);
	}

	private Problem problem(GoalSnapshot goal) {
		Map<Long, CategoryInfo> categories = catalog.categories()
			.stream()
			.collect(Collectors.toMap(CategoryInfo::id, Function.identity()));
		List<Need> needs = goal.requirements()
			.stream()
			.filter(r -> r.kind() != RequirementKind.OWNED)
			.map(r -> new Need(r.categoryId(), r.categorySlug(), categories.get(r.categoryId()).name(),
					r.kind() == RequirementKind.REQUIRED, r.quantity()))
			.toList();
		List<OwnedCategory> owned = goal.requirements()
			.stream()
			.filter(r -> r.kind() == RequirementKind.OWNED)
			.map(r -> new OwnedCategory(r.categorySlug(), categories.get(r.categoryId()).name()))
			.toList();
		Map<String, List<ProductFacts>> candidates = OutfitCandidates.load(catalog,
				needs.stream().map(Need::categoryId).toList(), categories, goal.fit(), java.util.Set.of());
		Map<String, String> names = categories.values()
			.stream()
			.collect(Collectors.toMap(CategoryInfo::slug, CategoryInfo::name));
		return new Problem(needs, candidates, compatibility.activeRules(), goal.budget().movePointRight(2).longValueExact(),
				new Space(goal.spaceWidthCm(), goal.spaceDepthCm()), owned,
				OutfitCandidates.context(goal), names);
	}

	/**
	 * The bundle's items with a size and color chosen per piece (see {@link OutfitFit}); the explanation says
	 * which option was picked so the shopper knows what they get.
	 */
	private List<NewItem> items(TierBundle bundle, Fit fit) {
		Map<Long, CategoryInfo> categories = catalog.categories()
			.stream()
			.collect(Collectors.toMap(CategoryInfo::id, Function.identity()));
		List<Pick> chosen = bundle.solution().chosen();
		Map<Long, VariantSummary> variants = OutfitFit.chooseVariants(chosen.stream()
			.map(p -> new OutfitFit.Piece(p.product().id(), categories.get(p.need().categoryId()).outfitSlot(),
					categories.get(p.need().categoryId()).sizeScale(), p.need().quantity(),
					catalog.variantsOf(p.product().id())))
			.toList(), fit);
		return chosen.stream().map(p -> {
			VariantSummary v = variants.get(p.product().id());
			String explanation = bundle.explanations().get(p.product().id());
			if (v != null && !"One size".equalsIgnoreCase(v.size())) {
				explanation = explanation + " In stock in your size (" + v.size() + "), shown in " + v.color() + ".";
			}
			else if (v != null && !"Standard".equals(v.color())) {
				explanation = explanation + " Shown in " + v.color() + ".";
			}
			return new NewItem(p.product().id(), v == null ? null : v.id(), p.need().categoryId(), p.need().quantity(),
					p.product().product().price(), !p.need().required(), explanation);
		}).toList();
	}

	/** The bundle summary plus one sentence on the outfit's colors. */
	private static String summary(TierBundle bundle, List<NewItem> items, ProductCatalog catalog) {
		List<VariantSummary> variants = new ArrayList<>(catalog
			.findVariants(items.stream().map(NewItem::variantId).filter(java.util.Objects::nonNull).toList())
			.values());
		String palette = OutfitFit.paletteNote(variants);
		return palette == null ? bundle.summary() : bundle.summary() + " " + palette;
	}

	private void saveMessages(UUID runId, String kind, List<String> messages) {
		for (int i = 0; i < messages.size(); i++) {
			jdbc.sql("INSERT INTO recommendation_run_messages (run_id, kind, message, position) VALUES (:run, :kind, :msg, :pos)")
				.param("run", runId)
				.param("kind", kind)
				.param("msg", messages.get(i))
				.param("pos", i)
				.update();
		}
	}
}
