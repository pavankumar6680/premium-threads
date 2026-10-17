package com.buildmybundle.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductQueriesTest {

	@Test
	void buildsPrefixQueryFromFreeText() {
		assertThat(ProductQueries.toPrefixTsQuery("Standing  Desk")).isEqualTo("standing:* & desk:*");
	}

	@Test
	void stripsTsQueryOperatorsSoUserInputCannotBreakTheSyntax() {
		assertThat(ProductQueries.toPrefixTsQuery("usb-c & !(4k) | 'x':*")).isEqualTo("usb:* & c:* & 4k:* & x:*");
	}

	@Test
	void ignoresBlankInput() {
		assertThat(ProductQueries.toPrefixTsQuery(null)).isNull();
		assertThat(ProductQueries.toPrefixTsQuery("  !!  ")).isNull();
	}

	@Test
	void limitsTheNumberOfTerms() {
		assertThat(ProductQueries.toPrefixTsQuery("a b c d e f g h i j").split(" & ")).hasSize(8);
	}
}
