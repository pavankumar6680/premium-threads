package com.buildmybundle.bundles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.buildmybundle.bundles.BundleModels.AccessorySuggestion;
import com.buildmybundle.bundles.BundleModels.Alternative;
import com.buildmybundle.bundles.BundleModels.BundleSummary;
import com.buildmybundle.bundles.BundleModels.BundleTier;
import com.buildmybundle.bundles.BundleModels.BundleView;
import com.buildmybundle.bundles.BundleModels.ChosenVariant;
import com.buildmybundle.bundles.BundleModels.EditableBundle;
import com.buildmybundle.bundles.BundleModels.ItemView;
import com.buildmybundle.bundles.BundleModels.NewBundle;
import com.buildmybundle.bundles.BundleModels.PurchaseLine;
import com.buildmybundle.bundles.BundleModels.SlotChoice;
import com.buildmybundle.bundles.domain.Bundle;
import com.buildmybundle.bundles.domain.BundleItem;
import com.buildmybundle.bundles.domain.BundleRepository;
import com.buildmybundle.catalog.CategoryInfo;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductFacts;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.catalog.VariantSummary;
import com.buildmybundle.compatibility.CompatibilityChecker;
import com.buildmybundle.compatibility.CompatibilityModels.Report;
import com.buildmybundle.compatibility.CompatibilityModels.Rule;
import com.buildmybundle.compatibility.CompatibilityModels.Severity;
import com.buildmybundle.compatibility.CompatibilityModels.Space;
import com.buildmybundle.compatibility.CompatibilityModels.Violation;
import com.buildmybundle.compatibility.CompatibilityService;
import com.buildmybundle.goals.GoalModels.Fit;
import com.buildmybundle.goals.GoalModels.GoalSnapshot;
import com.buildmybundle.goals.GoalModels.RequirementKind;
import com.buildmybundle.goals.GoalService;
import com.buildmybundle.goals.GoalViews.TemplateDetail;
import com.buildmybundle.pricing.PricingPolicy;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bundles and the bundle workspace. Recommended bundles are read-only; customers edit their own DRAFT copies.
 * Every edit refreshes prices and totals; compatibility is re-checked live on every read, so an edit may
 * leave the bundle incompatible, but such a bundle is reported as not purchasable until it is fixed.
 */
@Service
@Transactional(readOnly = true)
public class BundleService {

	static final String CHOSEN_BY_YOU = "Chosen by you.";
	static final int MAX_QUANTITY = 10;
	private static final int ACCESSORY_PRODUCTS = 3;

	private final BundleRepository bundles;
	private final ProductCatalog catalog;
	private final CompatibilityService compatibility;
	private final GoalService goals;

	public BundleService(BundleRepository bundles, ProductCatalog catalog, CompatibilityService compatibility,
			GoalService goals) {
		this.bundles = bundles;
		this.catalog = catalog;
		this.compatibility = compatibility;
		this.goals = goals;
	}

	// ---------------------------------------------------------------- recommended bundles

	@Transactional
	public UUID createRecommended(NewBundle input) {
		Bundle bundle = new Bundle(input.goalId(), input.runId(), input.userId(), input.tier().name(), input.name(),
				Bundle.RECOMMENDED, input.budget(), input.totalPrice(), input.score(), input.summary());
		input.items()
			.forEach(i -> bundle.addItem(i.productId(), i.categoryId(), i.quantity(), i.unitPrice(), i.optional(),
					i.explanation()).chooseVariant(i.variantId()));
		return bundles.save(bundle).getId();
	}

	/** Bundles of a recommendation run, in tier order (Budget, Balanced, Premium). */
	public List<BundleView> forRun(UUID runId) {
		return bundles.findByRunId(runId)
			.stream()
			.sorted(Comparator.comparing(b -> BundleTier.valueOf(b.getTier())))
			.map(b -> view(b, null))
			.toList();
	}

	// ---------------------------------------------------------------- reading

	public BundleView get(UUID id, Long viewerId) {
		return view(find(id), viewerId);
	}

	public List<BundleSummary> mine(Long userId) {
		Map<UUID, String> titles = new LinkedHashMap<>();
		return bundles.findByUserIdAndStatusInOrderByUpdatedAtDesc(userId, List.of(Bundle.DRAFT, Bundle.SAVED))
			.stream()
			.map(b -> new BundleSummary(b.getId(), b.getName(), BundleTier.valueOf(b.getTier()), b.getStatus(),
					b.getGoalId(), titles.computeIfAbsent(b.getGoalId(), this::goalTitle), b.getBudget(),
					b.getTotalPrice(), b.getItems().size(), b.getUpdatedAt()))
			.toList();
	}

	/** Product lines to put in the cart. Refuses bundles with a blocking incompatibility. */
	public List<PurchaseLine> purchaseLines(UUID id) {
		Bundle bundle = find(id);
		if (!report(bundle).compatible()) {
			throw ApiException.conflict("BUNDLE_INCOMPATIBLE",
					"This bundle has compatibility conflicts. Resolve them before adding it to your cart.");
		}
		List<PurchaseLine> lines = new ArrayList<>();
		List<Long> needSize = new ArrayList<>();
		for (BundleItem item : bundle.getItems()) {
			Long variantId = item.getVariantId() != null ? item.getVariantId() : onlyVariant(item.getProductId());
			if (variantId == null) {
				needSize.add(item.getProductId());
			}
			else {
				lines.add(new PurchaseLine(item.getProductId(), variantId, item.getQuantity()));
			}
		}
		if (!needSize.isEmpty()) {
			throw ApiException.conflict("SIZE_REQUIRED", "Choose a size and color for every piece before adding the outfit to your cart.")
				.with("productIds", needSize);
		}
		return lines;
	}

	/** The single active option of a product sold in one size and color, or null. */
	private Long onlyVariant(Long productId) {
		List<VariantSummary> options = catalog.variantsOf(productId)
			.stream()
			.filter(VariantSummary::active)
			.toList();
		return options.size() == 1 ? options.getFirst().id() : null;
	}

	// ---------------------------------------------------------------- workspace

	/** Creates an editable copy of any bundle the customer can see. */
	@Transactional
	public BundleView customize(UUID sourceId, Long userId) {
		Bundle copy = bundles.save(find(sourceId).draftCopy(userId));
		return view(copy, userId);
	}

	/** Puts another product of the same category in a slot. */
	@Transactional
	public BundleView replace(UUID id, Long userId, String categorySlug, Long productId) {
		Bundle bundle = editable(id, userId);
		CategoryInfo category = leaf(categorySlug);
		BundleItem item = slot(bundle, category);
		ProductSummary product = purchasable(productId, category, item.getQuantity());
		item.replaceProduct(product.id(), product.price(), CHOSEN_BY_YOU);
		dressItem(bundle, item);
		return saved(bundle, userId);
	}

	@Transactional
	public BundleView updateItem(UUID id, Long userId, String categorySlug, Integer quantity, Boolean locked,
			Long variantId) {
		Bundle bundle = editable(id, userId);
		BundleItem item = slot(bundle, leaf(categorySlug));
		if (quantity != null) {
			purchasable(item.getProductId(), leaf(categorySlug), quantity);
			item.setQuantity(quantity);
		}
		if (variantId != null) {
			VariantSummary variant = catalog.findVariant(variantId)
				.filter(v -> v.productId().equals(item.getProductId()))
				.orElseThrow(() -> ApiException.badRequest("VARIANT_MISMATCH", "This size or color belongs to another product."));
			if (!variant.canFulfil(item.getQuantity())) {
				throw ApiException.conflict("INSUFFICIENT_STOCK", "This size and color is not available in that quantity.")
					.with("available", variant.availableQuantity());
			}
			item.chooseVariant(variantId);
		}
		if (locked != null) {
			item.setLocked(locked);
		}
		return saved(bundle, userId);
	}

	/** Removes an optional item; must-haves are part of the goal and stay. */
	@Transactional
	public BundleView removeItem(UUID id, Long userId, String categorySlug) {
		Bundle bundle = editable(id, userId);
		BundleItem item = slot(bundle, leaf(categorySlug));
		if (!item.isOptional()) {
			throw ApiException.conflict("REQUIRED_ITEM",
					"This item is a must-have for your goal. Swap it for another product or adjust the goal instead.");
		}
		bundle.removeItem(item.getCategoryId());
		return saved(bundle, userId);
	}

	/** Adds a product from a category the bundle does not have yet (an accessory or extra). */
	@Transactional
	public BundleView addItem(UUID id, Long userId, Long productId, int quantity) {
		Bundle bundle = editable(id, userId);
		ProductSummary product = catalog.findById(productId)
			.orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist."));
		CategoryInfo category = leaf(product.categorySlug());
		if (bundle.item(category.id()).isPresent()) {
			throw ApiException.conflict("CATEGORY_ALREADY_IN_BUNDLE",
					"The bundle already has " + category.name().toLowerCase() + ". Swap it instead.");
		}
		purchasable(productId, category, quantity);
		dressItem(bundle, bundle.addItem(product.id(), category.id(), quantity, product.price(), true, CHOSEN_BY_YOU));
		return saved(bundle, userId);
	}

	@Transactional
	public BundleView setBudget(UUID id, Long userId, BigDecimal budget) {
		Bundle bundle = editable(id, userId);
		bundle.setBudget(PricingPolicy.money(budget));
		return saved(bundle, userId);
	}

	@Transactional
	public BundleView save(UUID id, Long userId, String name) {
		Bundle bundle = editable(id, userId);
		bundle.save(name.trim());
		return saved(bundle, userId);
	}

	@Transactional
	public void delete(UUID id, Long userId) {
		bundles.delete(editable(id, userId));
	}

	/** Every product that could fill a slot, with the price change and a compatibility verdict. */
	public List<Alternative> alternatives(UUID id, String categorySlug) {
		Bundle bundle = find(id);
		CategoryInfo category = leaf(categorySlug);
		BundleItem item = slot(bundle, category);
		List<ProductFacts> others = facts(bundle.getItems().stream()
			.filter(i -> !i.getCategoryId().equals(category.id()))
			.map(BundleItem::getProductId)
			.toList());
		Space space = space(bundle);
		List<Rule> rules = compatibility.activeRules();
		BigDecimal currentLine = catalog.findById(item.getProductId())
			.map(p -> p.price().multiply(BigDecimal.valueOf(item.getQuantity())))
			.orElse(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
		return catalog.activeFactsInCategories(List.of(category.id()))
			.stream()
			.map(candidate -> alternative(candidate, item.getQuantity(), currentLine, others, space, rules,
					candidate.id().equals(item.getProductId())))
			.sorted(Comparator.comparing(Alternative::current).reversed()
				.thenComparing(Comparator.comparing(Alternative::compatible).reversed())
				.thenComparing(Comparator.comparing(Alternative::inStock).reversed())
				.thenComparing(Alternative::price))
			.toList();
	}

	/**
	 * Categories worth adding: those linked by a compatibility rule to something in the bundle (an arm for a
	 * monitor, a lens for a camera) and the goal's other categories, each with its best compatible products.
	 */
	public List<AccessorySuggestion> accessories(UUID id) {
		Bundle bundle = find(id);
		Map<String, CategoryInfo> bySlug = catalog.categories()
			.stream()
			.collect(Collectors.toMap(CategoryInfo::slug, Function.identity()));
		List<ProductFacts> current = facts(bundle.getItems().stream().map(BundleItem::getProductId).toList());
		Set<String> present = current.stream().map(ProductFacts::categorySlug).collect(Collectors.toSet());
		Optional<GoalSnapshot> goal = goals.find(bundle.getGoalId());
		Set<String> owned = goal.map(g -> g.requirements().stream()
			.filter(r -> r.kind() == RequirementKind.OWNED)
			.map(GoalSnapshot.Requirement::categorySlug)
			.collect(Collectors.toSet())).orElse(Set.of());
		List<Rule> rules = compatibility.activeRules();

		Map<String, String> reasons = new LinkedHashMap<>();
		for (Rule rule : rules) {
			if (rule.operator().spatial() || rule.universal()) {
				continue;
			}
			for (ProductFacts p : current) {
				if (rule.involves(p.categorySlug())) {
					String other = p.categorySlug().equals(rule.sourceCategory()) ? rule.targetCategory() : rule.sourceCategory();
					if (other != null && !present.contains(other) && !owned.contains(other)) {
						reasons.putIfAbsent(other, "Works with your " + p.product().name());
					}
				}
			}
		}
		goal.ifPresent(g -> g.requirements().stream()
			.filter(r -> r.kind() != RequirementKind.OWNED && !present.contains(r.categorySlug()))
			.forEach(r -> reasons.putIfAbsent(r.categorySlug(), "Part of your goal")));
		// categories of the goal's template the customer did not pick (e.g. lighting for a photo studio)
		goal.map(GoalSnapshot::templateSlug).flatMap(this::template).ifPresent(t -> t.items()
			.stream()
			.map(i -> i.category().slug())
			.filter(slug -> !present.contains(slug) && !owned.contains(slug))
			.forEach(slug -> reasons.putIfAbsent(slug, "Popular for " + t.title().toLowerCase() + " setups")));

		Space space = space(bundle);
		List<Long> categoryIds = reasons.keySet().stream().map(bySlug::get).map(CategoryInfo::id).toList();
		Map<String, List<ProductFacts>> candidates = catalog.activeFactsInCategories(categoryIds)
			.stream()
			.collect(Collectors.groupingBy(ProductFacts::categorySlug));
		List<AccessorySuggestion> suggestions = new ArrayList<>();
		reasons.forEach((slug, reason) -> {
			List<Alternative> best = candidates.getOrDefault(slug, List.of())
				.stream()
				.map(c -> alternative(c, 1, BigDecimal.ZERO, current, space, rules, false))
				.filter(a -> a.compatible() && a.inStock())
				.sorted(Comparator.comparing(Alternative::ratingAvg).reversed()
					.thenComparing(Comparator.comparingInt(Alternative::ratingCount).reversed()))
				.limit(ACCESSORY_PRODUCTS)
				.toList();
			if (!best.isEmpty()) {
				suggestions.add(new AccessorySuggestion(slug, bySlug.get(slug).name(), reason, best));
			}
		});
		return suggestions;
	}

	// ---------------------------------------------------------------- re-optimization support

	public EditableBundle loadForEdit(UUID id, Long userId) {
		Bundle bundle = editable(id, userId);
		return new EditableBundle(bundle.getId(), bundle.getGoalId(), BundleTier.valueOf(bundle.getTier()),
				bundle.getBudget(),
				bundle.getItems().stream()
					.map(i -> new EditableBundle.Slot(i.getCategoryId(), i.getProductId(), i.getQuantity(),
							i.isOptional(), i.isLocked()))
					.toList());
	}

	/** Applies the optimizer's choices slot by slot (locked slots keep their product). */
	@Transactional
	public BundleView applyChoices(UUID id, Long userId, List<SlotChoice> choices, String summary) {
		Bundle bundle = editable(id, userId);
		for (SlotChoice choice : choices) {
			bundle.item(choice.categoryId())
				.filter(item -> !item.isLocked() && !item.getProductId().equals(choice.productId()))
				.ifPresent(item -> item.replaceProduct(choice.productId(), choice.unitPrice(), choice.explanation()));
		}
		bundle.getItems().stream().filter(i -> i.getVariantId() == null).forEach(i -> dressItem(bundle, i));
		bundle.updateTotals(bundle.getTotalPrice(), summary);
		return saved(bundle, userId);
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Picks the size and color for an item from the goal's fit (the wearer's size, preferred colors, at most one
	 * accent color across the outfit). Leaves it unset when the size is unknown and the product has several.
	 */
	private void dressItem(Bundle bundle, BundleItem item) {
		Fit fit = goals.find(bundle.getGoalId()).map(GoalSnapshot::fit).orElse(Fit.NONE);
		Map<Long, CategoryInfo> categories = catalog.categories()
			.stream()
			.collect(Collectors.toMap(CategoryInfo::id, Function.identity()));
		List<String> accents = bundle.getItems()
			.stream()
			.filter(i -> i != item && i.getVariantId() != null)
			.map(i -> catalog.findVariant(i.getVariantId()).orElse(null))
			.filter(v -> v != null && "ACCENT".equals(v.colorFamily()))
			.map(VariantSummary::color)
			.distinct()
			.toList();
		CategoryInfo category = categories.get(item.getCategoryId());
		VariantSummary chosen = OutfitFit.choose(new OutfitFit.Piece(item.getProductId(), category.outfitSlot(),
				category.sizeScale(), item.getQuantity(), catalog.variantsOf(item.getProductId())), fit, new ArrayList<>(accents));
		item.chooseVariant(chosen == null ? null : chosen.id());
	}

	private Alternative alternative(ProductFacts candidate, int quantity, BigDecimal currentLine,
			List<ProductFacts> others, Space space, List<Rule> rules, boolean isCurrent) {
		List<String> issues = new ArrayList<>();
		for (ProductFacts other : others) {
			CompatibilityChecker.pairViolations(candidate, other, rules)
				.stream()
				.filter(v -> v.severity() == Severity.BLOCK)
				.forEach(v -> issues.add(v.message()));
		}
		CompatibilityChecker.spaceViolations(candidate, space, rules).stream().map(Violation::message).forEach(issues::add);
		List<ProductFacts> withCandidate = new ArrayList<>(others);
		withCandidate.add(candidate);
		CompatibilityChecker.presenceViolations(withCandidate, rules)
			.stream()
			.filter(v -> v.productIds().contains(candidate.id()))
			.map(Violation::message)
			.forEach(issues::add);
		ProductSummary p = candidate.product();
		BigDecimal line = p.price().multiply(BigDecimal.valueOf(quantity));
		return new Alternative(p.id(), p.slug(), p.name(), p.brand(), p.price(),
				PricingPolicy.money(line.subtract(currentLine)), p.ratingAvg(), p.ratingCount(), p.availableQuantity(),
				p.availableQuantity() >= quantity, issues.isEmpty(), List.copyOf(new LinkedHashSet<>(issues)), isCurrent);
	}

	/** Refreshes prices and totals after an edit and returns the new view. */
	private BundleView saved(Bundle bundle, Long viewerId) {
		Map<Long, ProductSummary> products = catalog.findByIds(bundle.getItems().stream().map(BundleItem::getProductId).toList());
		BigDecimal total = BigDecimal.ZERO;
		for (BundleItem item : bundle.getItems()) {
			ProductSummary p = products.get(item.getProductId());
			if (p != null) {
				item.refreshPrice(p.price());
			}
			total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
		}
		bundle.updateTotals(PricingPolicy.money(total), null);
		return view(bundles.saveAndFlush(bundle), viewerId);
	}

	private Bundle find(UUID id) {
		return bundles.findWithItemsById(id).orElseThrow(BundleService::notFound);
	}

	/** The customer's own editable bundle; anything else looks like it does not exist (or is read-only). */
	private Bundle editable(UUID id, Long userId) {
		Bundle bundle = find(id);
		if (userId == null || !userId.equals(bundle.getUserId())) {
			if (!bundle.editable()) {
				throw readOnly();
			}
			throw notFound();
		}
		if (!bundle.editable()) {
			throw readOnly();
		}
		return bundle;
	}

	private CategoryInfo leaf(String slug) {
		return catalog.categories()
			.stream()
			.filter(c -> c.leaf() && c.slug().equals(slug))
			.findFirst()
			.orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY", "Unknown category: " + slug));
	}

	private static BundleItem slot(Bundle bundle, CategoryInfo category) {
		return bundle.item(category.id())
			.orElseThrow(() -> ApiException.notFound("BUNDLE_ITEM_NOT_FOUND",
					"The bundle has no " + category.name().toLowerCase() + "."));
	}

	private ProductSummary purchasable(Long productId, CategoryInfo category, int quantity) {
		if (quantity < 1 || quantity > MAX_QUANTITY) {
			throw ApiException.badRequest("QUANTITY_LIMIT", "Choose between 1 and " + MAX_QUANTITY + ".");
		}
		ProductSummary product = catalog.findById(productId)
			.orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist."));
		if (!product.categorySlug().equals(category.slug())) {
			throw ApiException.badRequest("WRONG_CATEGORY", product.name() + " is not in " + category.name() + ".");
		}
		if (!product.canFulfil(quantity)) {
			throw ApiException.conflict("INSUFFICIENT_STOCK", product.availableQuantity() == 0
					? product.name() + " is out of stock."
					: "Only " + product.availableQuantity() + " of " + product.name() + " left in stock.")
				.with("available", product.availableQuantity());
		}
		return product;
	}

	private List<ProductFacts> facts(List<Long> productIds) {
		return catalog.facts(productIds);
	}

	private Space space(Bundle bundle) {
		return goals.find(bundle.getGoalId())
			.map(g -> new Space(g.spaceWidthCm(), g.spaceDepthCm()))
			.orElse(Space.UNKNOWN);
	}

	private Report report(Bundle bundle) {
		return compatibility.check(bundle.getItems().stream().map(BundleItem::getProductId).toList(), space(bundle));
	}

	private Optional<TemplateDetail> template(String slug) {
		try {
			return Optional.of(goals.template(slug));
		}
		catch (ApiException ex) {
			return Optional.empty(); // template since deactivated
		}
	}

	private String goalTitle(UUID goalId) {
		return goals.find(goalId).map(GoalSnapshot::title).orElse("Goal");
	}

	private BundleView view(Bundle bundle, Long viewerId) {
		List<BundleItem> items = bundle.getItems();
		Map<Long, ProductSummary> products = catalog.findByIds(items.stream().map(BundleItem::getProductId).toList());
		Map<Long, VariantSummary> variants = catalog.findVariants(
				items.stream().map(BundleItem::getVariantId).filter(java.util.Objects::nonNull).toList());
		Map<Long, Long> optionCounts = new java.util.HashMap<>();
		items.stream()
			.filter(i -> i.getVariantId() == null)
			.forEach(i -> optionCounts.put(i.getProductId(),
					catalog.variantsOf(i.getProductId()).stream().filter(VariantSummary::active).count()));
		Map<Long, CategoryInfo> categories = catalog.categories()
			.stream()
			.collect(Collectors.toMap(CategoryInfo::id, Function.identity()));
		List<ItemView> views = items.stream().map(i -> {
			ProductSummary p = products.get(i.getProductId());
			CategoryInfo c = categories.get(i.getCategoryId());
			BigDecimal current = p == null ? null : p.price();
			BigDecimal lineTotal = PricingPolicy.money((current == null ? i.getUnitPrice() : current)
				.multiply(BigDecimal.valueOf(i.getQuantity())));
			VariantSummary v = i.getVariantId() == null ? null : variants.get(i.getVariantId());
			boolean needsSize = v == null && optionCounts.getOrDefault(i.getProductId(), 0L) > 1;
			boolean available = p != null && p.canFulfil(i.getQuantity()) && (v == null || v.canFulfil(i.getQuantity()));
			return new ItemView(i.getProductId(), p == null ? null : p.slug(), v != null ? v.sku() : p == null ? null : p.sku(),
					p == null ? "Unavailable product" : p.name(), p == null ? null : p.brand(), c.slug(), c.name(),
					i.getQuantity(), i.getUnitPrice(), current, lineTotal, i.isOptional(), i.isLocked(),
					i.getExplanation(), p == null ? BigDecimal.ZERO : p.ratingAvg(), p == null ? 0 : p.ratingCount(),
					v != null ? v.availableQuantity() : p == null ? 0 : p.availableQuantity(), available,
					v == null ? null : new ChosenVariant(v.id(), v.size(), v.color(), v.colorHex(), v.availableQuantity()),
					needsSize);
		}).toList();
		BigDecimal currentTotal = views.stream().map(ItemView::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		Report report = report(bundle);
		boolean purchasable = !views.isEmpty() && views.stream().allMatch(ItemView::available)
				&& views.stream().noneMatch(ItemView::needsSize) && report.compatible();
		boolean editable = bundle.editable() && viewerId != null && viewerId.equals(bundle.getUserId());
		return new BundleView(bundle.getId(), bundle.getGoalId(), goalTitle(bundle.getGoalId()), bundle.getRunId(),
				bundle.getSourceBundleId(), BundleTier.valueOf(bundle.getTier()), bundle.getName(), bundle.getStatus(),
				bundle.getBudget(), bundle.getTotalPrice(), currentTotal, bundle.getSummary(), views, report,
				purchasable, editable, bundle.getCreatedAt(), bundle.getUpdatedAt());
	}

	private static ApiException notFound() {
		return ApiException.notFound("BUNDLE_NOT_FOUND", "This bundle does not exist.");
	}

	private static ApiException readOnly() {
		return ApiException.conflict("BUNDLE_READ_ONLY",
				"Recommended bundles cannot be changed. Customize the bundle to get your own editable copy.");
	}
}
