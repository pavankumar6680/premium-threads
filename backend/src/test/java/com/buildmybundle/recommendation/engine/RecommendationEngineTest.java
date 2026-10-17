package com.buildmybundle.recommendation.engine;

import static com.buildmybundle.recommendation.engine.TestProducts.product;
import static com.buildmybundle.recommendation.engine.TestProducts.rule;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.compatibility.CompatibilityModels.Operator;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Space;
import com.buildmybundle.recommendation.engine.BundleOptimizer.Limits;
import com.buildmybundle.recommendation.engine.EngineModels.EngineResult;
import com.buildmybundle.recommendation.engine.EngineModels.Need;
import com.buildmybundle.recommendation.engine.EngineModels.OwnedCategory;
import com.buildmybundle.recommendation.engine.EngineModels.Problem;
import com.buildmybundle.recommendation.engine.EngineModels.ScoringContext;
import com.buildmybundle.recommendation.engine.EngineModels.Tier;
import com.buildmybundle.recommendation.engine.EngineModels.TierBundle;

import org.junit.jupiter.api.Test;

class RecommendationEngineTest {

	private static final Rule LENS_MOUNT = rule("LENS_MOUNT", "lenses", "cameras", Operator.EQUALS, "mount", "mount");
	private static final Rule DESK_WIDTH = new Rule("DESK_WIDTH", "desks", null, Operator.LTE_SPACE_WIDTH, "width_cm",
			null, null, com.buildmybundle.compatibility.CompatibilityModels.Severity.BLOCK, "fits your available width",
			"too wide");

	private final List<ProductFacts> desks = List.of(
			product(1, "desks", "129").rating(4.2, 860).tier(1).attr("width_cm", "100").tags("home-office").build(),
			product(2, "desks", "329").rating(4.5, 2140).tier(2).attr("width_cm", "120").tags("home-office", "ergonomic").build(),
			product(3, "desks", "649").rating(4.8, 1580).tier(3).attr("width_cm", "160").tags("home-office", "ergonomic").build());
	private final List<ProductFacts> chairs = List.of(
			product(11, "chairs", "99").rating(4.0, 1720).tier(1).tags("home-office").build(),
			product(12, "chairs", "249").rating(4.5, 2380).tier(2).tags("home-office", "ergonomic").build(),
			product(13, "chairs", "589").rating(4.8, 940).tier(3).tags("home-office", "ergonomic").build());
	private final List<ProductFacts> lamps = List.of(
			product(21, "lamps", "29").rating(4.1, 2200).tier(1).build(),
			product(22, "lamps", "169").rating(4.7, 720).tier(3).build());

	@Test
	void producesThreeDistinctTiersWithinBudgetFromCheapestToBest() {
		EngineResult result = recommend(problem(1200, List.of(need("desks", true), need("chairs", true), need("lamps", false)),
				context("home-office", List.of("ergonomic")), Space.UNKNOWN, List.of()));

		assertThat(result.bundles()).extracting(TierBundle::tier).containsExactly(Tier.BUDGET, Tier.BALANCED, Tier.PREMIUM);
		long budget = total(result, Tier.BUDGET);
		long balanced = total(result, Tier.BALANCED);
		long premium = total(result, Tier.PREMIUM);
		assertThat(budget).isLessThan(balanced).isLessThanOrEqualTo(120_000);
		assertThat(premium).isGreaterThanOrEqualTo(balanced).isLessThanOrEqualTo(120_000);
		// the budget tier covers must-haves only
		assertThat(bundle(result, Tier.BUDGET).solution().chosen()).allMatch(p -> p.need().required());
		assertThat(result.infeasibility()).isNull();
		assertThat(result.complete()).isTrue();
	}

	@Test
	void explainsEachPickWithCatalogFacts() {
		EngineResult result = recommend(problem(1200, List.of(need("desks", true), need("chairs", true)),
				context("home-office", List.of()), Space.UNKNOWN, List.of()));

		String budgetDesk = bundle(result, Tier.BUDGET).explanations().get(1L);
		assertThat(budgetDesk).startsWith("The lowest-priced option in Desks").contains("rated 4.2/5 by 860 customers")
			.contains("made for home office");
		assertThat(bundle(result, Tier.BUDGET).summary()).contains("The lowest-cost way to cover all 2 must-haves");
	}

	@Test
	void doesNotRepeatIdenticalTiers() {
		Problem problem = problem(1200, List.of(need("desks", true)), context(null, List.of()), Space.UNKNOWN, List.of());
		Problem single = new Problem(problem.needs(), Map.of("desks", List.of(desks.getFirst())), problem.rules(),
				problem.budgetCents(), problem.space(), problem.owned(), problem.context(), problem.categoryNames());

		EngineResult result = recommend(single);

		assertThat(result.bundles()).hasSize(1);
		assertThat(result.notes()).hasSize(2).allMatch(n -> n.contains("identical"));
	}

	@Test
	void reportsTheMinimumFeasibleCostAndWhatToDropWhenTheBudgetIsTooLow() {
		EngineResult result = recommend(problem(200, List.of(need("desks", true), need("chairs", true)),
				context(null, List.of()), Space.UNKNOWN, List.of()));

		assertThat(result.bundles()).isEmpty();
		assertThat(result.infeasibility().minimumFeasibleCents()).isEqualTo(22_800); // 129 + 99
		assertThat(result.infeasibility().suggestions()).contains("Raise your budget to at least $228.00.",
				"Mark Desks as already owned or remove it: the remaining must-haves start at $99.00.",
				"Mark Chairs as already owned or remove it: the remaining must-haves start at $129.00.");
	}

	@Test
	void explainsSpaceConstraintsThatRuleOutACategory() {
		EngineResult result = recommend(problem(2000, List.of(need("desks", true)), context(null, List.of()),
				new Space(BigDecimal.valueOf(90), null), List.of(DESK_WIDTH)));

		assertThat(result.infeasibility().reasons()).containsExactly("No Desks fit your available space.");
	}

	@Test
	void keepsOnlyProductsThatFitTheSpace() {
		EngineResult result = recommend(problem(2000, List.of(need("desks", true)), context(null, List.of()),
				new Space(BigDecimal.valueOf(125), null), List.of(DESK_WIDTH)));

		assertThat(result.bundles())
			.allSatisfy(b -> assertThat(b.solution().chosen().getFirst().product().id()).isIn(1L, 2L));
		TierBundle withWiderDesk = result.bundles().stream()
			.filter(b -> b.explanations().containsKey(2L))
			.findFirst()
			.orElseThrow();
		assertThat(withWiderDesk.explanations().get(2L)).contains("fits your available width");
	}

	@Test
	void identifiesIncompatibleCategories() {
		List<ProductFacts> cameras = List.of(product(31, "cameras", "700").attr("mount", "FX").build());
		List<ProductFacts> lenses = List.of(product(41, "lenses", "300").attr("mount", "OM").build());
		Map<String, List<ProductFacts>> candidates = new LinkedHashMap<>();
		candidates.put("cameras", cameras);
		candidates.put("lenses", lenses);
		Problem problem = new Problem(List.of(need("cameras", true), need("lenses", true)), candidates, List.of(LENS_MOUNT),
				500_000, Space.UNKNOWN, List.of(), context(null, List.of()), Map.of());

		EngineResult result = recommend(problem);

		assertThat(result.bundles()).isEmpty();
		assertThat(result.infeasibility().minimumFeasibleCents()).isNull();
		assertThat(result.infeasibility().reasons()).singleElement().asString()
			.contains("None of the available Cameras work with any available Lenses");
	}

	@Test
	void fallsBackToGeneralProductsWhenPurposeMadeOnesDoNotFit() {
		List<ProductFacts> pcs = List.of(
				product(51, "desktops", "449").tags("home-office").build(),
				product(52, "desktops", "999").tags("gaming").build());
		Problem problem = new Problem(List.of(need("desktops", true)), Map.of("desktops", pcs), List.of(), 60_000,
				Space.UNKNOWN, List.of(), context("gaming", List.of()), Map.of());

		EngineResult result = recommend(problem);

		TierBundle bundle = result.bundles().getFirst();
		assertThat(bundle.contextRelaxed()).isTrue();
		assertThat(bundle.summary()).contains("general-purpose products because none made for gaming fit the budget");
	}

	@Test
	void prefersPurposeMadeProductsWhenAffordable() {
		List<ProductFacts> pcs = List.of(
				product(51, "desktops", "449").rating(4.9, 3000).tier(3).tags("home-office").build(),
				product(52, "desktops", "999").rating(4.3, 100).tier(1).tags("gaming").build());
		Problem problem = new Problem(List.of(need("desktops", true)), Map.of("desktops", pcs), List.of(), 200_000,
				Space.UNKNOWN, List.of(), context("gaming", List.of()), Map.of());

		EngineResult result = recommend(problem);

		assertThat(result.bundles()).allSatisfy(b -> {
			assertThat(b.solution().chosen().getFirst().product().id()).isEqualTo(52L);
			assertThat(b.contextRelaxed()).isFalse();
		});
	}

	@Test
	void remindsCustomersToCheckOwnedEquipment() {
		Rule armLoad = rule("ARM_LOAD", "monitors", "monitor-arms", Operator.LTE, "weight_kg", "max_load_kg");
		List<ProductFacts> arms = List.of(product(61, "monitor-arms", "89").attr("max_load_kg", "9").build());
		Problem problem = new Problem(List.of(need("monitor-arms", true)), Map.of("monitor-arms", arms), List.of(armLoad),
				50_000, Space.UNKNOWN, List.of(new OwnedCategory("monitors", "Monitors")), context(null, List.of()),
				Map.of("monitor-arms", "Monitor Arms"));

		EngineResult result = recommend(problem);

		assertThat(result.notes()).contains(
				"You already own monitors: check that it works with the new monitor arms (arm_load).");
	}

	// ------------------------------------------------------------------ helpers

	private static EngineResult recommend(Problem problem) {
		return RecommendationEngine.recommend(problem, Limits.DEFAULT);
	}

	private Problem problem(int budgetDollars, List<Need> needs, ScoringContext context, Space space, List<Rule> rules) {
		Map<String, List<ProductFacts>> candidates = new LinkedHashMap<>();
		List<ProductFacts> all = new ArrayList<>();
		all.addAll(desks);
		all.addAll(chairs);
		all.addAll(lamps);
		all.forEach(p -> candidates.computeIfAbsent(p.categorySlug(), k -> new ArrayList<>()).add(p));
		return new Problem(needs, candidates, rules, budgetDollars * 100L, space, List.of(), context, Map.of());
	}

	private static Need need(String category, boolean required) {
		return new Need((long) category.hashCode(), category,
				Character.toUpperCase(category.charAt(0)) + category.substring(1), required, 1);
	}

	private static ScoringContext context(String tag, List<String> usage) {
		return new ScoringContext(tag, usage, List.of());
	}

	private static TierBundle bundle(EngineResult result, Tier tier) {
		return result.bundles().stream().filter(b -> b.tier() == tier).findFirst().orElseThrow();
	}

	private static long total(EngineResult result, Tier tier) {
		return bundle(result, tier).solution().costCents();
	}
}
