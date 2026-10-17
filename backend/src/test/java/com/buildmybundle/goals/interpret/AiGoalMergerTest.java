package com.buildmybundle.goals.interpret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import com.buildmybundle.goals.GoalModels.Quality;
import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.interpret.AiGoalMerger.AiGoal;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateItem;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateTerms;

import org.junit.jupiter.api.Test;

class AiGoalMergerTest {

	private static final InterpreterVocabulary VOCABULARY = new InterpreterVocabulary(
			List.of(new TemplateTerms("gaming-setup", "Gaming setup", List.of("gaming"),
					List.of(new TemplateItem("desktops", true, true, 1), new TemplateItem("monitors", true, true, 1),
							new TemplateItem("webcams", false, false, 1)),
					List.of("gaming", "streaming"))),
			new LinkedHashMap<>(VocabularyLoader.SYNONYMS), Set.of("Corvex", "Pixelon"), Set.of("gaming", "streaming", "compact"));

	private static GoalAnalysis rules(String text) {
		return RuleBasedGoalInterpreter.interpret(text, VOCABULARY);
	}

	@Test
	void parsesJsonEvenWhenWrappedInProse() {
		assertThat(AiGoalMerger.parse("Sure! ```json\n{\"templateSlug\":\"gaming-setup\",\"budget\":1500}\n```"))
			.hasValueSatisfying(g -> {
				assertThat(g.templateSlug()).isEqualTo("gaming-setup");
				assertThat(g.budget()).isEqualByComparingTo("1500");
			});
		assertThat(AiGoalMerger.parse("I cannot help with that.")).isEmpty();
		assertThat(AiGoalMerger.parse("{not json}")).isEmpty();
	}

	@Test
	void ignoresEverythingTheShopperDidNotSay() {
		String text = "I stream while gaming and need a new setup, budget 1500";
		AiGoal ai = AiGoalMerger.parse("""
				{"templateSlug":"space-station","quality":"PREMIUM",
				 "categories":[{"slug":"rgb-fans","kind":"REQUIRED"},{"slug":"webcams","kind":"REQUIRED"},
				               {"slug":"desks","kind":"OWNED"}],
				 "usageTags":["streaming","teleportation"],"preferredBrands":["corvex","Razer"],
				 "clarifyingQuestions":["Do you need a microphone?"]}""").orElseThrow();

		GoalAnalysis merged = AiGoalMerger.merge(rules(text), ai, text, VOCABULARY);

		assertThat(merged.source()).isEqualTo("AI");
		assertThat(merged.templateSlug()).isEqualTo("gaming-setup"); // the rules' match wins
		assertThat(merged.items()).extracting(GoalAnalysis.Item::categorySlug)
			.doesNotContain("rgb-fans", "webcams", "desks"); // valid store categories, but never mentioned
		assertThat(merged.usageTags()).contains("streaming").doesNotContain("teleportation");
		assertThat(merged.preferredBrands()).isEmpty(); // Corvex exists, but the shopper did not ask for it
		assertThat(merged.quality()).isEqualTo(Quality.BALANCED);
		assertThat(merged.clarifications()).isEmpty(); // nothing essential is missing
	}

	@Test
	void letsTheModelRecogniseOwnedItemsTheRulesMissed() {
		String text = "Gaming rig please. My old monitor still works fine, 2k to spend";
		assertThat(rules(text).items()).extracting(GoalAnalysis.Item::kind).doesNotContain(RequirementKind.OWNED);
		AiGoal ai = AiGoalMerger.parse("""
				{"categories":[{"slug":"monitors","kind":"OWNED"}]}""").orElseThrow();

		GoalAnalysis merged = AiGoalMerger.merge(rules(text), ai, text, VOCABULARY);

		assertThat(merged.items()).extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("monitors", RequirementKind.OWNED), tuple("desktops", RequirementKind.REQUIRED));
	}

	@Test
	void neverReadsRequestedProductsAsOwned() {
		String text = "Home office for 1200. I need a desk, a chair and a monitor. My keyboard is fine";
		AiGoal ai = AiGoalMerger.parse("""
				{"categories":[{"slug":"desks","kind":"OWNED"},{"slug":"monitors","kind":"OWNED"},
				               {"slug":"keyboards","kind":"OWNED"}]}""").orElseThrow();

		GoalAnalysis merged = AiGoalMerger.merge(rules(text), ai, text, VOCABULARY);

		assertThat(merged.items()).extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("keyboards", RequirementKind.OWNED))
			.doesNotContain(tuple("desks", RequirementKind.OWNED), tuple("monitors", RequirementKind.OWNED));
	}

	@Test
	void trustsAModelBudgetOnlyWhenTheNumberIsInTheText() {
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("1500"), "I can spend around one thousand five hundred, 1,500 max")).isEqualByComparingTo("1500");
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("2000"), "about 2 thousand")).isEqualByComparingTo("2000");
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("2500"), "a 2.5k budget")).isEqualByComparingTo("2500");
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("1200"), "a decent gaming PC")).isNull(); // invented
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("1200"), "about 11200")).isNull(); // only a substring
	}

	@Test
	void usesTheModelsTemplateAndDefaultsWhenTheRulesFoundNone() {
		String text = "I want to play the latest shooters at high frame rates";
		assertThat(rules(text).templateSlug()).isNull();
		AiGoal ai = AiGoalMerger.parse("""
				{"templateSlug":"gaming-setup","clarifyingQuestions":["Do you already have a monitor?","x"]}""").orElseThrow();

		GoalAnalysis merged = AiGoalMerger.merge(rules(text), ai, text, VOCABULARY);

		assertThat(merged.title()).isEqualTo("Gaming setup");
		assertThat(merged.items()).extracting(GoalAnalysis.Item::categorySlug).containsExactly("desktops", "monitors");
		assertThat(merged.clarifications()).containsExactly("What is your total budget?", "Do you already have a monitor?");
	}
}
