package com.buildmybundle.goals.interpret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.buildmybundle.goals.GoalModels.Quality;
import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateItem;
import com.buildmybundle.goals.interpret.InterpreterVocabulary.TemplateTerms;

import org.junit.jupiter.api.Test;

class RuleBasedGoalInterpreterTest {

	private static final InterpreterVocabulary VOCABULARY = vocabulary();

	private static GoalAnalysis interpret(String text) {
		return RuleBasedGoalInterpreter.interpret(text, VOCABULARY);
	}

	@Test
	void interpretsTheReferenceHomeOfficeRequest() {
		GoalAnalysis analysis = interpret("I want to set up a professional work-from-home office with a budget of "
				+ "$1,200. I need a desk, ergonomic chair, monitor, keyboard, mouse, and lighting.");

		assertThat(analysis.templateSlug()).isEqualTo("home-office");
		assertThat(analysis.title()).isEqualTo("Professional home office");
		assertThat(analysis.budget()).isEqualByComparingTo("1200.00");
		assertThat(analysis.quality()).isEqualTo(Quality.BALANCED);
		assertThat(analysis.items()).extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.containsExactly(tuple("desks", RequirementKind.REQUIRED), tuple("chairs", RequirementKind.REQUIRED),
					tuple("monitors", RequirementKind.REQUIRED), tuple("keyboards", RequirementKind.REQUIRED),
					tuple("mice", RequirementKind.REQUIRED),
					// "lighting" resolves to the template's desk lamps, and is upgraded from optional to required
					tuple("desk-lamps", RequirementKind.REQUIRED), tuple("webcams", RequirementKind.OPTIONAL));
		assertThat(analysis.usageTags()).containsExactly("ergonomic");
		assertThat(analysis.clarifications()).isEmpty();
		assertThat(analysis.source()).isEqualTo("RULES");
	}

	@Test
	void recognisesEquipmentTheCustomerAlreadyOwns() {
		GoalAnalysis analysis = interpret(
				"I already have a monitor and keyboard, but I need a desk and chair for my home office. Budget around 1.5k");

		assertThat(analysis.items()).extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("monitors", RequirementKind.OWNED), tuple("keyboards", RequirementKind.OWNED),
					tuple("desks", RequirementKind.REQUIRED), tuple("chairs", RequirementKind.REQUIRED));
		assertThat(analysis.budget()).isEqualByComparingTo("1500.00");
	}

	@Test
	void recognisesHandMeDownsAsOwned() {
		assertThat(interpret("Got my brother's old monitor, need a gaming PC").items())
			.extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("monitors", RequirementKind.OWNED));
		assertThat(interpret("My dad gave me his desk, I need a chair").items())
			.extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("desks", RequirementKind.OWNED), tuple("chairs", RequirementKind.REQUIRED));
	}

	@Test
	void resolvesAmbiguousWordsInFavourOfTheMatchedTemplate() {
		GoalAnalysis analysis = interpret("Photography setup with studio lighting, max $3k");

		assertThat(analysis.templateSlug()).isEqualTo("photo-studio");
		assertThat(analysis.budget()).isEqualByComparingTo("3000.00");
		assertThat(analysis.items()).extracting(GoalAnalysis.Item::categorySlug).contains("studio-lighting")
			.doesNotContain("desk-lamps", "smart-lighting");
	}

	@Test
	void doesNotMistakeResolutionsForBudgets() {
		GoalAnalysis analysis = interpret("A 4k monitor for gaming");

		assertThat(analysis.templateSlug()).isEqualTo("gaming-setup");
		assertThat(analysis.budget()).isNull();
		assertThat(analysis.clarifications()).containsExactly("What is your total budget?");
	}

	@Test
	void buildsACustomGoalWhenNoTemplateMatches() {
		GoalAnalysis analysis = interpret("Something cheap: a desk and two chairs, 300 dollars");

		assertThat(analysis.templateSlug()).isNull();
		assertThat(analysis.title()).isEqualTo("Custom bundle");
		assertThat(analysis.quality()).isEqualTo(Quality.BUDGET);
		assertThat(analysis.budget()).isEqualByComparingTo("300.00");
		assertThat(analysis.items()).extracting(GoalAnalysis.Item::categorySlug).containsExactly("desks", "chairs");
	}

	@Test
	void detectsPreferredBrandsAndPremiumIntent() {
		GoalAnalysis analysis = interpret("A high-end Ergolift standing desk for my office, budget $900");

		assertThat(analysis.preferredBrands()).containsExactly("Ergolift");
		assertThat(analysis.quality()).isEqualTo(Quality.PREMIUM);
		assertThat(analysis.matchedTerms()).contains("Ergolift", "standing desk", "$900");
	}

	@Test
	void asksForClarificationWhenNothingIsRecognised() {
		GoalAnalysis analysis = interpret("hello there");

		assertThat(analysis.items()).isEmpty();
		assertThat(analysis.clarifications()).hasSize(2);
	}

	@Test
	void parsesCommonBudgetFormats() {
		assertThat(budget("budget is $2,499.99")).isEqualByComparingTo("2499.99");
		assertThat(budget("spend up to 750")).isEqualByComparingTo("750");
		assertThat(budget("under 2k")).isEqualByComparingTo("2000");
		assertThat(budget("$1.5k total")).isEqualByComparingTo("1500");
		assertThat(budget("a 27 inch monitor")).isNull();
	}

	private static java.math.BigDecimal budget(String text) {
		return RuleBasedGoalInterpreter.extractBudget(RuleBasedGoalInterpreter.normalize(text), new ArrayList<>());
	}

	private static InterpreterVocabulary vocabulary() {
		TemplateTerms homeOffice = new TemplateTerms("home-office", "Professional home office",
				List.of("home office", "work from home", "office"),
				List.of(item("desks", true, true), item("chairs", true, true), item("monitors", true, true),
						item("keyboards", true, true), item("mice", true, true), item("desk-lamps", false, true),
						item("webcams", false, true), item("monitor-arms", false, false)),
				List.of("ergonomic", "streaming", "compact", "wireless"));
		TemplateTerms gaming = new TemplateTerms("gaming-setup", "Gaming setup", List.of("gaming", "gamer"),
				List.of(item("desktops", true, true), item("monitors", true, true)), List.of("gaming", "streaming"));
		TemplateTerms photo = new TemplateTerms("photo-studio", "Photography studio",
				List.of("photography", "photo"),
				List.of(item("cameras", true, true), item("lenses", true, true), item("studio-lighting", true, true)),
				List.of("beginner", "pro"));
		Map<String, List<String>> phrases = new LinkedHashMap<>(VocabularyLoader.SYNONYMS);
		return new InterpreterVocabulary(List.of(homeOffice, gaming, photo), phrases, Set.of("Ergolift", "Vantix"),
				Set.of("ergonomic", "streaming", "compact", "wireless", "gaming", "beginner", "pro"));
	}

	private static TemplateItem item(String slug, boolean required, boolean selected) {
		return new TemplateItem(slug, required, selected, 1);
	}
}
