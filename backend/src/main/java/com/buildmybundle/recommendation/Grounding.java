package com.buildmybundle.recommendation;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that model-written text does not introduce numbers that are absent from the facts it was given:
 * the most common and most harmful hallucination in shopping advice is a wrong price or total. Small counts
 * (0–10) are allowed because they are used naturally ("three bundles", "2 must-haves"). Brand names are checked
 * the same way, so the model cannot recommend a brand that is not in the outfit.
 */
final class Grounding {

	private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
	private static final int FREE_SMALL_NUMBER = 10;

	private Grounding() {
	}

	static boolean numbersGrounded(String text, String facts) {
		Set<String> known = numbers(facts);
		for (String number : numbers(text)) {
			if (!known.contains(number) && !isSmall(number)) {
				return false;
			}
		}
		return true;
	}

	/** True when every catalog brand the text names is one of the brands in the facts. */
	static boolean brandsGrounded(String text, String facts, Set<String> catalogBrands) {
		String lowerText = text.toLowerCase(java.util.Locale.ROOT);
		String lowerFacts = facts.toLowerCase(java.util.Locale.ROOT);
		for (String brand : catalogBrands) {
			Pattern p = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(brand.toLowerCase(java.util.Locale.ROOT)) + "(?![\\p{L}])");
			if (p.matcher(lowerText).find() && !p.matcher(lowerFacts).find()) {
				return false;
			}
		}
		return true;
	}

	static Set<String> numbers(String text) {
		Set<String> numbers = new HashSet<>();
		Matcher m = NUMBER.matcher(text);
		while (m.find()) {
			numbers.add(normalize(m.group()));
		}
		return numbers;
	}

	/** "$1,154.00" → "1154", "4.50" → "4.5": the same value written differently compares equal. */
	static String normalize(String number) {
		String plain = number.replace(",", "");
		try {
			return new BigDecimal(plain).stripTrailingZeros().toPlainString();
		}
		catch (NumberFormatException ex) {
			return plain;
		}
	}

	private static boolean isSmall(String number) {
		try {
			BigDecimal value = new BigDecimal(number);
			return value.scale() <= 0 && value.compareTo(BigDecimal.valueOf(FREE_SMALL_NUMBER)) <= 0;
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}
}
