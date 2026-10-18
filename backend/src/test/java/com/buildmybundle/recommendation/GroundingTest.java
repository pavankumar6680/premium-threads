package com.buildmybundle.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroundingTest {

	private static final String FACTS = "Budget: $1,200.00. Budget bundle: total $594.00, 50% of the budget, rated 4.5/5.";

	@Test
	void acceptsNumbersFromTheFactsInAnyFormat() {
		assertThat(Grounding.numbersGrounded("The budget bundle costs $594, half (50%) of your $1200 budget, rated 4.5.", FACTS))
			.isTrue();
	}

	@Test
	void rejectsInventedNumbers() {
		assertThat(Grounding.numbersGrounded("The budget bundle costs $549.", FACTS)).isFalse();
		assertThat(Grounding.numbersGrounded("It saves you 30% compared with buying separately.", FACTS)).isFalse();
	}

	@Test
	void allowsSmallCounts() {
		assertThat(Grounding.numbersGrounded("There are 3 bundles and 2 of them leave money to spare.", FACTS)).isTrue();
	}

	@Test
	void rejectsBrandsThatAreNotInTheOutfit() {
		String facts = "Tops: Classic Polo by Tommy Hilfiger ($55.99, size M, Navy); Jeans: Slim Jeans by H&M ($29.99).";
		java.util.Set<String> brands = java.util.Set.of("Tommy Hilfiger", "H&M", "Ralph Lauren", "Guess");
		assertThat(Grounding.brandsGrounded("The Tommy Hilfiger polo and H&M jeans keep it relaxed.", facts, brands)).isTrue();
		assertThat(Grounding.brandsGrounded("Swap in a Ralph Lauren shirt for a dressier look.", facts, brands)).isFalse();
		// a brand name inside another word is not a mention
		assertThat(Grounding.brandsGrounded("I guessed you would like navy.", facts, brands)).isTrue();
	}
}
