package com.buildmybundle.goals.interpret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.buildmybundle.goals.GoalModels.Fit;
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
	void interpretsTheReferenceOutfitRequest() {
		GoalAnalysis analysis = interpret("I want a stylish casual outfit for a weekend party under $200 for him. "
				+ "I need a shirt, jeans and a watch.");

		assertThat(analysis.templateSlug()).isEqualTo("weekend-casual-men");
		assertThat(analysis.title()).isEqualTo("Weekend casual (men)");
		assertThat(analysis.budget()).isEqualByComparingTo("200.00");
		assertThat(analysis.quality()).isEqualTo(Quality.BALANCED);
		assertThat(analysis.items()).extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("men-shirts", RequirementKind.REQUIRED), tuple("men-jeans", RequirementKind.REQUIRED),
					// the template's optional watch is upgraded because it was asked for
					tuple("watches", RequirementKind.REQUIRED), tuple("men-tshirts", RequirementKind.REQUIRED))
			.noneMatch(t -> t.toList().getFirst().toString().startsWith("women-"));
		assertThat(analysis.usageTags()).contains("casual", "party-wear");
		assertThat(analysis.clarifications()).isEmpty();
		assertThat(analysis.source()).isEqualTo("RULES");
	}

	@Test
	void recognisesPiecesTheCustomerAlreadyOwns() {
		GoalAnalysis analysis = interpret(
				"I already have jeans and a jacket, but I need a shirt for the office. Budget around 150, for men");

		assertThat(analysis.templateSlug()).isEqualTo("office-men");
		assertThat(analysis.items()).extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("men-jeans", RequirementKind.OWNED), tuple("men-jackets", RequirementKind.OWNED),
					tuple("men-shirts", RequirementKind.REQUIRED));
		assertThat(analysis.budget()).isEqualByComparingTo("150.00");
	}

	@Test
	void recognisesHandMeDownsAsOwned() {
		assertThat(interpret("Got my brother's old watch, need an office shirt for him").items())
			.extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("watches", RequirementKind.OWNED), tuple("men-shirts", RequirementKind.REQUIRED));
		assertThat(interpret("My mom gave me her handbag, I need a party dress").items())
			.extracting(GoalAnalysis.Item::categorySlug, GoalAnalysis.Item::kind)
			.contains(tuple("handbags", RequirementKind.OWNED), tuple("women-dresses", RequirementKind.REQUIRED));
	}

	@Test
	void garmentWordsFollowTheWardrobeOfTheMatchedTemplate() {
		GoalAnalysis analysis = interpret("Office look for women: a blazer and trousers, max $300");

		assertThat(analysis.templateSlug()).isEqualTo("office-women");
		assertThat(analysis.budget()).isEqualByComparingTo("300.00");
		assertThat(analysis.items()).extracting(GoalAnalysis.Item::categorySlug)
			.contains("women-jackets", "women-trousers")
			.doesNotContain("men-jackets", "men-trousers");
	}

	@Test
	void asksForTheBudgetWhenItIsMissing() {
		GoalAnalysis analysis = interpret("A party dress for her, size 8");

		assertThat(analysis.templateSlug()).isEqualTo("party-women");
		assertThat(analysis.budget()).isNull(); // a size is not a budget
		assertThat(analysis.clarifications()).containsExactly("What is your total budget?");
	}

	@Test
	void buildsACustomGoalWhenNoTemplateMatches() {
		GoalAnalysis analysis = interpret("Something cheap: a scarf and two caps, 60 dollars");

		assertThat(analysis.templateSlug()).isNull();
		assertThat(analysis.title()).isEqualTo("Custom bundle");
		assertThat(analysis.quality()).isEqualTo(Quality.BUDGET);
		assertThat(analysis.budget()).isEqualByComparingTo("60.00");
		assertThat(analysis.items()).extracting(GoalAnalysis.Item::categorySlug).containsExactly("scarves", "caps");
	}

	@Test
	void detectsPreferredBrandsAndPremiumIntent() {
		GoalAnalysis analysis = interpret("A luxury Michael Kors watch for the office, budget $300");

		assertThat(analysis.preferredBrands()).containsExactly("Michael Kors");
		assertThat(analysis.quality()).isEqualTo(Quality.PREMIUM);
		assertThat(analysis.matchedTerms()).contains("Michael Kors", "watch", "$300");
	}

	@Test
	void asksForClarificationWhenNothingIsRecognised() {
		GoalAnalysis analysis = interpret("hello there");

		assertThat(analysis.items()).isEmpty();
		assertThat(analysis.clarifications()).hasSize(2);
	}

	@Test
	void parsesCommonBudgetFormats() {
		assertThat(budget("budget is $249.99")).isEqualByComparingTo("249.99");
		assertThat(budget("spend up to 150")).isEqualByComparingTo("150");
		assertThat(budget("under 1k")).isEqualByComparingTo("1000");
		assertThat(budget("$1.5k total")).isEqualByComparingTo("1500");
		assertThat(budget("waist 32 jeans")).isNull();
	}

	@Test
	void readsWardrobeSizesAndColorsOnlyWhenStated() {
		Fit fit = interpret("Office outfit for her, size M, waist 28, in navy and rose gold, around $250").fit();
		assertThat(fit.gender()).isEqualTo("WOMEN");
		assertThat(fit.sizeAlpha()).isEqualTo("M");
		assertThat(fit.sizeWaist()).isEqualTo("28");
		// "rose gold" is one color, not also "gold"; only catalog colors are recognised
		assertThat(fit.colors()).containsExactly("Navy", "Rose Gold");

		Fit spoken = interpret("I wear a large and 32 waist jeans, black please").fit();
		assertThat(spoken.sizeAlpha()).isEqualTo("L");
		assertThat(spoken.sizeWaist()).isEqualTo("32");
		assertThat(spoken.colors()).containsExactly("Black");

		Fit silent = interpret("Something nice for a party").fit();
		assertThat(silent.gender()).isEqualTo("WOMEN"); // from the matched women's party template
		assertThat(silent.sizeAlpha()).isNull();
		assertThat(silent.sizeWaist()).isNull();
		assertThat(silent.colors()).isEmpty();
		assertThat(interpret("a mauve jacket").fit().colors()).as("not a catalog color").isEmpty();
	}

	private static java.math.BigDecimal budget(String text) {
		return RuleBasedGoalInterpreter.extractBudget(RuleBasedGoalInterpreter.normalize(text), new ArrayList<>());
	}

	private static InterpreterVocabulary vocabulary() {
		List<String> men = List.of("men", "him", "guy");
		List<String> women = List.of("women", "her", "girl");
		TemplateTerms weekendMen = template("weekend-casual-men", "Weekend casual (men)", List.of("weekend", "casual"), men,
				List.of(item("men-tshirts", true, true), item("men-jeans", true, true), item("men-jackets", false, true),
						item("sunglasses", false, true), item("watches", false, false)));
		TemplateTerms officeMen = template("office-men", "Office ready (men)", List.of("office", "work"), men,
				List.of(item("men-shirts", true, true), item("men-trousers", true, true), item("belts", false, true)));
		TemplateTerms officeWomen = template("office-women", "Office ready (women)", List.of("office", "work"), women,
				List.of(item("women-tops", true, true), item("women-trousers", true, true),
						item("women-jackets", false, true)));
		TemplateTerms partyWomen = template("party-women", "Party night (women)", List.of("party", "night out"), women,
				List.of(item("women-dresses", true, true), item("handbags", false, true), item("jewelry", false, true)));
		Map<String, List<String>> phrases = new LinkedHashMap<>(VocabularyLoader.SYNONYMS);
		return new InterpreterVocabulary(List.of(weekendMen, officeMen, officeWomen, partyWomen), phrases,
				Set.of("Michael Kors", "Levi's", "Zara"), Set.of("casual", "streetwear", "formal", "minimalist", "party-wear"),
				Set.of("Navy", "Black", "White", "Gold", "Rose Gold", "Olive"));
	}

	private static TemplateTerms template(String slug, String title, List<String> occasion, List<String> wardrobe,
			List<TemplateItem> items) {
		List<String> keywords = new ArrayList<>(occasion);
		keywords.addAll(wardrobe);
		return new TemplateTerms(slug, title, keywords, items,
				List.of("casual", "streetwear", "formal", "minimalist", "party-wear"));
	}

	private static TemplateItem item(String slug, boolean required, boolean selected) {
		return new TemplateItem(slug, required, selected, 1);
	}
}
