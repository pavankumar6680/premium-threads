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
			List.of(new TemplateTerms("streetwear-men", "Streetwear (men)", List.of("streetwear", "guy", "men"),
					List.of(new TemplateItem("men-hoodies", true, true, 1), new TemplateItem("men-jeans", true, true, 1),
							new TemplateItem("caps", false, false, 1)),
					List.of("streetwear", "casual"))),
			new LinkedHashMap<>(VocabularyLoader.SYNONYMS), Set.of("Nike", "Puma"), Set.of("streetwear", "casual", "formal"));

	private static GoalAnalysis rules(String text) {
		return RuleBasedGoalInterpreter.interpret(text, VOCABULARY);
	}

	@Test
	void parsesJsonEvenWhenWrappedInProse() {
		assertThat(AiGoalMerger.parse("Sure! ```json\n{\"templateSlug\":\"streetwear-men\",\"budget\":150}\n```"))
			.hasValueSatisfying(g -> {
				assertThat(g.templateSlug()).isEqualTo("streetwear-men");
				assertThat(g.budget()).isEqualByComparingTo("150");
			});
		assertThat(AiGoalMerger.parse("I cannot help with that.")).isEmpty();
		assertThat(AiGoalMerger.parse("{not json}")).isEmpty();
	}

	@Test
	void ignoresEverythingTheShopperDidNotSay() {
		String text = "Streetwear for a guy, I need a new hoodie, budget 150";
		AiGoal ai = AiGoalMerger.parse("""
				{"templateSlug":"space-station","quality":"PREMIUM",
				 "categories":[{"slug":"rgb-fans","kind":"REQUIRED"},{"slug":"caps","kind":"REQUIRED"},
				               {"slug":"belts","kind":"OWNED"}],
				 "usageTags":["streetwear","teleportation"],"preferredBrands":["nike","Supreme"],
				 "clarifyingQuestions":["Do you need sneakers?"]}""").orElseThrow();

		GoalAnalysis merged = AiGoalMerger.merge(rules(text), ai, text, VOCABULARY);

		assertThat(merged.source()).isEqualTo("AI");
		assertThat(merged.templateSlug()).isEqualTo("streetwear-men"); // the rules' match wins
		assertThat(merged.items()).extracting(GoalAnalysis.Item::categorySlug)
			.doesNotContain("rgb-fans", "caps", "belts"); // valid store categories, but never mentioned
		assertThat(merged.usageTags()).contains("streetwear").doesNotContain("teleportation");
		assertThat(merged.preferredBrands()).isEmpty(); // Nike exists, but the shopper did not ask for it
		assertThat(merged.quality()).isEqualTo(Quality.BALANCED);
		assertThat(merged.clarifications()).isEmpty(); // nothing essential is missing
	}

	@Test
	void letsTheModelRecogniseOwnedItemsTheRulesMissed() {
		String text = "Streetwear look for a guy, budget 150. My old jeans still fit fine";
		assertThat(rules(text).items()).extracting(GoalAnalysis.Item::kind).doesNotContain(RequirementKind.OWNED);
		AiGoal ai = AiGoalMerger.parse("""
				{"categories":[{"slug":"men-jeans","kind":"OWNED"}]}""").orElseThrow();

		GoalAnalysis merged = AiGoalMerger.merge(rules(text), ai, text, VOCABULARY);

		assertThat(merged.items()).extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("men-jeans", RequirementKind.OWNED), tuple("men-hoodies", RequirementKind.REQUIRED));
	}

	@Test
	void neverReadsRequestedProductsAsOwned() {
		String text = "Streetwear for a guy, budget 150. I need a hoodie, jeans and a cap. My belt is fine";
		AiGoal ai = AiGoalMerger.parse("""
				{"categories":[{"slug":"men-hoodies","kind":"OWNED"},{"slug":"men-jeans","kind":"OWNED"},
				               {"slug":"belts","kind":"OWNED"}]}""").orElseThrow();

		GoalAnalysis merged = AiGoalMerger.merge(rules(text), ai, text, VOCABULARY);

		assertThat(merged.items()).extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("belts", RequirementKind.OWNED))
			.doesNotContain(tuple("men-hoodies", RequirementKind.OWNED), tuple("men-jeans", RequirementKind.OWNED));
	}

	@Test
	void trustsAModelBudgetOnlyWhenTheNumberIsInTheText() {
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("150"), "I can spend around one hundred and fifty, 150 max")).isEqualByComparingTo("150");
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("2000"), "about 2 thousand")).isEqualByComparingTo("2000");
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("1500"), "a 1.5k budget")).isEqualByComparingTo("1500");
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("200"), "a nice party outfit")).isNull(); // invented
		assertThat(AiGoalMerger.groundedBudget(new BigDecimal("200"), "about 1200")).isNull(); // only a substring
	}

	@Test
	void usesTheModelsTemplateAndDefaultsWhenTheRulesFoundNone() {
		String text = "I want to look like a skater at the skate park";
		assertThat(rules(text).templateSlug()).isNull();
		AiGoal ai = AiGoalMerger.parse("""
				{"templateSlug":"streetwear-men","clarifyingQuestions":["Do you already have jeans?","x"]}""").orElseThrow();

		GoalAnalysis merged = AiGoalMerger.merge(rules(text), ai, text, VOCABULARY);

		assertThat(merged.title()).isEqualTo("Streetwear (men)");
		assertThat(merged.items()).extracting(GoalAnalysis.Item::categorySlug).containsExactly("men-hoodies", "men-jeans");
		assertThat(merged.clarifications()).containsExactly("What is your total budget?", "Do you already have jeans?");
	}

	@org.junit.jupiter.api.Test
	void parsesATypicalModelAnswer() {
		String answer = "{\"templateSlug\": \"date-night-women\", \"budget\": 250, \"categories\": [{\"slug\": \"women-dresses\"}], "
				+ "\"usageTags\": [\"formal\", \"party-wear\"], \"clarifyingQuestions\": []}";
		org.assertj.core.api.Assertions.assertThat(AiGoalMerger.parse(answer)).isPresent();
		// small models also answer with bare slugs and lone values
		org.assertj.core.api.Assertions.assertThat(AiGoalMerger.parse(
				"{\"templateSlug\": \"office-men\", \"categories\": [\"men-shirts\"], \"usageTags\": \"formal\"}"))
			.hasValueSatisfying(ai -> {
				org.assertj.core.api.Assertions.assertThat(ai.categories()).extracting(AiGoalMerger.AiCategory::slug).containsExactly("men-shirts");
				org.assertj.core.api.Assertions.assertThat(ai.usageTags()).containsExactly("formal");
			});
	}
}
