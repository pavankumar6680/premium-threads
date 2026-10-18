package com.buildmybundle.cart;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.buildmybundle.cart.domain.Cart;
import com.buildmybundle.cart.domain.CartItem;
import com.buildmybundle.cart.domain.CartRepository;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.catalog.VariantSummary;
import com.buildmybundle.pricing.PriceBreakdown;
import com.buildmybundle.pricing.PricingPolicy;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shopping cart. Stores only variant ids (size and color of a product) and quantities; prices, availability
 * and totals are always computed from the live catalog and the pricing policy, never trusted from the client.
 */
@Service
@Transactional
public class CartService {

	public static final int MAX_LINE_QUANTITY = 99;
	static final int MAX_LINES = 50;

	private final CartRepository carts;
	private final ProductCatalog catalog;
	private final PricingPolicy pricing;

	public CartService(CartRepository carts, ProductCatalog catalog, PricingPolicy pricing) {
		this.carts = carts;
		this.catalog = catalog;
		this.pricing = pricing;
	}

	@Transactional(readOnly = true)
	public CartView view(Long userId) {
		return carts.findByUserId(userId).map(this::toView).orElseGet(() -> toView(List.of()));
	}

	@Transactional(readOnly = true)
	public List<CartLine> lines(Long userId) {
		return carts.findByUserId(userId)
			.map(cart -> cart.getItems()
				.stream()
				.map(i -> new CartLine(i.getProductId(), i.getVariantId(), i.getQuantity()))
				.toList())
			.orElse(List.of());
	}

	/**
	 * Resolves what the customer picked: a variant, or a product that is sold in a single option. A product
	 * with several sizes or colors needs an explicit choice.
	 */
	@Transactional(readOnly = true)
	public Long resolveVariant(Long productId, Long variantId) {
		if (variantId != null) {
			VariantSummary variant = catalog.findVariant(variantId)
				.orElseThrow(() -> ApiException.notFound("VARIANT_NOT_FOUND", "This size or color does not exist."));
			if (productId != null && !productId.equals(variant.productId())) {
				throw ApiException.badRequest("VARIANT_MISMATCH", "This size or color belongs to another product.");
			}
			return variantId;
		}
		if (productId == null) {
			throw ApiException.badRequest("VARIANT_REQUIRED", "Choose a size and color.");
		}
		List<VariantSummary> options = catalog.variantsOf(productId).stream().filter(VariantSummary::active).toList();
		if (options.isEmpty()) {
			throw ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist.");
		}
		if (options.size() > 1) {
			throw ApiException.badRequest("VARIANT_REQUIRED", "Choose a size and color.").with("productId", productId);
		}
		return options.getFirst().id();
	}

	/** Adds {@code quantity} units of a variant (merging with an existing line). */
	public CartView addItem(Long userId, Long variantId, int quantity) {
		Cart cart = getOrCreate(userId);
		int current = cart.item(variantId).map(CartItem::getQuantity).orElse(0);
		if (current == 0 && cart.getItems().size() >= MAX_LINES) {
			throw ApiException.conflict("CART_FULL", "Your cart can hold up to " + MAX_LINES + " different items.");
		}
		int target = current + quantity;
		VariantSummary variant = checkPurchasable(variantId, target);
		cart.put(variant.productId(), variantId, target);
		return toView(cart);
	}

	/**
	 * Adds several variants at once, e.g. a whole outfit. All-or-nothing: every line is validated (with the
	 * quantities already in the cart) before anything changes.
	 */
	public CartView addLines(Long userId, List<CartLine> lines) {
		Cart cart = getOrCreate(userId);
		Map<Long, Integer> targets = new LinkedHashMap<>();
		for (CartLine line : lines) {
			int current = targets.getOrDefault(line.variantId(),
					cart.item(line.variantId()).map(CartItem::getQuantity).orElse(0));
			targets.put(line.variantId(), current + line.quantity());
		}
		long newLines = targets.keySet().stream().filter(id -> cart.item(id).isEmpty()).count();
		if (cart.getItems().size() + newLines > MAX_LINES) {
			throw ApiException.conflict("CART_FULL", "Your cart can hold up to " + MAX_LINES + " different items.");
		}
		Map<Long, VariantSummary> checked = new LinkedHashMap<>();
		targets.forEach((variantId, quantity) -> checked.put(variantId, checkPurchasable(variantId, quantity)));
		targets.forEach((variantId, quantity) -> cart.put(checked.get(variantId).productId(), variantId, quantity));
		return toView(cart);
	}

	public CartView setQuantity(Long userId, Long variantId, int quantity) {
		Cart cart = carts.findByUserId(userId).orElseThrow(CartService::itemNotFound);
		CartItem item = cart.item(variantId).orElseThrow(CartService::itemNotFound);
		if (quantity > item.getQuantity()) {
			checkPurchasable(variantId, quantity); // reducing a quantity is always allowed
		}
		cart.put(item.getProductId(), variantId, quantity);
		return toView(cart);
	}

	public CartView removeItem(Long userId, Long variantId) {
		Cart cart = carts.findByUserId(userId).orElseThrow(CartService::itemNotFound);
		cart.item(variantId).orElseThrow(CartService::itemNotFound);
		cart.remove(variantId);
		return toView(cart);
	}

	public void clear(Long userId) {
		carts.findByUserId(userId).ifPresent(Cart::clear);
	}

	private VariantSummary checkPurchasable(Long variantId, int quantity) {
		if (quantity > MAX_LINE_QUANTITY) {
			throw ApiException.badRequest("QUANTITY_LIMIT", "You can buy at most " + MAX_LINE_QUANTITY + " of an item.");
		}
		VariantSummary variant = catalog.findVariant(variantId)
			.orElseThrow(() -> ApiException.notFound("VARIANT_NOT_FOUND", "This size or color does not exist."));
		ProductSummary product = catalog.findById(variant.productId())
			.orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist."));
		String what = variant.label().isEmpty() ? product.name() : product.name() + " (" + variant.label() + ")";
		if (!product.active() || !variant.active()) {
			throw ApiException.conflict("PRODUCT_UNAVAILABLE", what + " is no longer available.");
		}
		if (variant.availableQuantity() < quantity) {
			throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
					variant.availableQuantity() == 0 ? what + " is out of stock."
							: "Only " + variant.availableQuantity() + " of " + what + " left in stock.")
				.with("available", variant.availableQuantity())
				.with("variantId", variantId);
		}
		return variant;
	}

	private Cart getOrCreate(Long userId) {
		return carts.findByUserId(userId).orElseGet(() -> {
			carts.insertIfMissing(userId);
			return carts.findByUserId(userId).orElseThrow();
		});
	}

	private CartView toView(Cart cart) {
		return toView(cart.getItems());
	}

	private CartView toView(List<CartItem> lines) {
		Map<Long, ProductSummary> products = catalog.findByIds(lines.stream().map(CartItem::getProductId).toList());
		Map<Long, VariantSummary> variants = catalog.findVariants(lines.stream().map(CartItem::getVariantId).toList());
		List<CartView.Item> items = lines.stream().map(line -> {
			ProductSummary p = products.get(line.getProductId());
			VariantSummary v = variants.get(line.getVariantId());
			BigDecimal unitPrice = p.price();
			boolean available = p.active() && v.canFulfil(line.getQuantity());
			return new CartView.Item(p.id(), v.id(), v.sku(), p.slug(), p.name(), p.brand(), p.categorySlug(), v.size(),
					v.color(), v.colorHex(), unitPrice, line.getQuantity(),
					PricingPolicy.money(unitPrice.multiply(BigDecimal.valueOf(line.getQuantity()))), v.availableQuantity(),
					available);
		}).toList();
		BigDecimal subtotal = items.stream().map(CartView.Item::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		PriceBreakdown price = pricing.quote(subtotal);
		int itemCount = items.stream().mapToInt(CartView.Item::quantity).sum();
		boolean ready = !items.isEmpty() && items.stream().allMatch(CartView.Item::available);
		return new CartView(items, itemCount, price.subtotal(), price.shipping(), price.tax(), price.total(),
				price.freeShippingThreshold(), ready);
	}

	private static ApiException itemNotFound() {
		return ApiException.notFound("CART_ITEM_NOT_FOUND", "This item is not in your cart.");
	}
}
