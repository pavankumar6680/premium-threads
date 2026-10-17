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
}
