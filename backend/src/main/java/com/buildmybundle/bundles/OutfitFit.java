package com.buildmybundle.bundles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.catalog.VariantSummary;
import com.buildmybundle.goals.GoalModels.Fit;

/**
 * Fashion fit for outfits, a pure function of its inputs:
 * <ul>
 * <li>which products can be worn by the wearer (wardrobe, in stock in their size);</li>
 * <li>which colorway of each chosen piece to use. Pieces are colored in a fixed order (full looks and tops,
 * then bottoms, layers, accessories). The shopper's colors come first; otherwise neutrals, denim and metals
 * form the base and at most one accent color is used, so the outfit reads as coordinated rather than random.</li>
 * </ul>
 */
public final class OutfitFit {

	private static final List<String> SLOT_ORDER = List.of("FULL", "TOP", "BOTTOM", "LAYER", "ACCESSORY");
	private static final Set<String> BASE_FAMILIES = Set.of("NEUTRAL", "DENIM", "METAL");

	private OutfitFit() {
	}

	/** True when the wearer can buy this product: right wardrobe and in stock in their size for its size scale. */
	public static boolean fits(ProductFacts product, Fit fit, String sizeScale) {
		if (fit.gender() != null && !product.values(ProductFacts.WARDROBE).contains(fit.gender())) {
			return false;
		}
		return product.inStockInSize(fit.sizeFor(sizeScale));
	}

	/** True when the product comes in one of the wearer's colors, in stock in their size. */
	public static boolean offersPreferredColor(ProductFacts product, Fit fit, String sizeScale) {
		String size = fit.sizeFor(sizeScale);
		return fit.colors().stream().anyMatch(c -> product.inStock(size, c));
	}

	/** One chosen piece of an outfit, with every variant of its product. */
	public record Piece(Long productId, String outfitSlot, String sizeScale, int quantity, List<VariantSummary> options) {
	}

	/**
	 * Chooses a variant for each piece. A piece is left without a variant (absent from the result) when its size
	 * is unknown and it is sold in several sizes, or when nothing is in stock in the wearer's size.
	 */
	public static Map<Long, VariantSummary> chooseVariants(List<Piece> pieces, Fit fit) {
		Map<Long, VariantSummary> chosen = new LinkedHashMap<>();
		List<String> accents = new ArrayList<>();
		List<Piece> ordered = pieces.stream()
			.sorted(Comparator.comparingInt((Piece p) -> slotRank(p.outfitSlot())))
			.toList();
		for (Piece piece : ordered) {
			VariantSummary best = choose(piece, fit, accents);
			if (best != null) {
				chosen.put(piece.productId(), best);
				if ("ACCENT".equals(best.colorFamily()) && !accents.contains(best.color())) {
					accents.add(best.color());
				}
			}
		}
		return chosen;
	}

	/** The variant for one piece given the accents already used, or null when the shopper must choose a size. */
	public static VariantSummary choose(Piece piece, Fit fit, List<String> accentsUsed) {
		List<VariantSummary> available = piece.options()
			.stream()
			.filter(v -> v.canFulfil(piece.quantity()))
			.toList();
		String size = fit.sizeFor(piece.sizeScale());
		if (size != null) {
			available = available.stream().filter(v -> v.size().equals(size)).toList();
		}
		else if (available.stream().map(VariantSummary::size).distinct().count() > 1) {
			return null; // several sizes and we do not know which one: the shopper picks
		}
		Map<String, Integer> preferred = new HashMap<>();
		for (int i = 0; i < fit.colors().size(); i++) {
			preferred.put(fit.colors().get(i).toLowerCase(Locale.ROOT), i);
		}
		return available.stream()
			.max(Comparator.comparingInt((VariantSummary v) -> colorScore(v, preferred, accentsUsed))
				.thenComparingInt(VariantSummary::availableQuantity))
			.orElse(null);
	}

	private static int colorScore(VariantSummary v, Map<String, Integer> preferred, List<String> accentsUsed) {
		Integer rank = preferred.get(v.color().toLowerCase(Locale.ROOT));
		if (rank != null) {
			return 100 - rank; // the shopper's first color beats their second
		}
		if (BASE_FAMILIES.contains(v.colorFamily())) {
			return 10;
		}
		if (accentsUsed.isEmpty() || accentsUsed.contains(v.color())) {
			return 8; // one accent color is a deliberate pop of color
		}
		return 0; // a second accent clashes
	}

	private static int slotRank(String slot) {
		int index = SLOT_ORDER.indexOf(slot);
		return index < 0 ? SLOT_ORDER.size() : index;
	}

	/** One short sentence about the outfit's palette, e.g. "A neutral base of Navy and White with Coral as the accent." */
	public static String paletteNote(List<VariantSummary> variants) {
		List<String> base = variants.stream()
			.filter(v -> BASE_FAMILIES.contains(v.colorFamily()) && !"Standard".equals(v.color()))
			.map(VariantSummary::color)
			.distinct()
			.toList();
		List<String> accent = variants.stream()
			.filter(v -> "ACCENT".equals(v.colorFamily()))
			.map(VariantSummary::color)
			.distinct()
			.toList();
		if (base.isEmpty() && accent.isEmpty()) {
			return null;
		}
		String baseText = base.isEmpty() ? "" : "a base of " + join(base);
		String accentText = accent.isEmpty() ? "" : join(accent) + (accent.size() == 1 ? " as the accent" : " as accents");
		String sentence = base.isEmpty() ? accentText : accent.isEmpty() ? baseText + " that goes with everything" : baseText + " with " + accentText;
		return "Colors: " + sentence + ".";
	}

	private static String join(List<String> values) {
		if (values.size() == 1) {
			return values.getFirst();
		}
		return String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.getLast();
	}
}
