package com.buildmybundle.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.buildmybundle.cart.domain.Cart;
import com.buildmybundle.cart.domain.CartItem;
import com.buildmybundle.cart.domain.CartRepository;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.pricing.PriceBreakdown;
import com.buildmybundle.pricing.PricingPolicy;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shopping cart. Stores only product ids and quantities; prices, availability and totals are always
 * computed from the live catalog and the pricing policy, never trusted from the client.
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
			.map(cart -> cart.getItems().stream().map(i -> new CartLine(i.getProductId(), i.getQuantity())).toList())
			.orElse(List.of());
	}

	/** Adds {@code quantity} units (merging with an existing line). */
	public CartView addItem(Long userId, Long productId, int quantity) {
		Cart cart = getOrCreate(userId);
		int current = cart.item(productId).map(CartItem::getQuantity).orElse(0);
		if (current == 0 && cart.getItems().size() >= MAX_LINES) {
			throw ApiException.conflict("CART_FULL", "Your cart can hold up to " + MAX_LINES + " different products.");
		}
		int target = current + quantity;
		checkPurchasable(productId, target);
		cart.put(productId, target);
		return toView(cart);
	}

	/**
	 * Adds several products at once, e.g. a whole bundle. All-or-nothing: every line is validated (with the
	 * quantities already in the cart) before anything changes.
	 */
	public CartView addLines(Long userId, List<CartLine> lines) {
		Cart cart = getOrCreate(userId);
		Map<Long, Integer> targets = new java.util.LinkedHashMap<>();
		for (CartLine line : lines) {
			int current = targets.getOrDefault(line.productId(),
					cart.item(line.productId()).map(CartItem::getQuantity).orElse(0));
			targets.put(line.productId(), current + line.quantity());
		}
		long newLines = targets.keySet().stream().filter(id -> cart.item(id).isEmpty()).count();
		if (cart.getItems().size() + newLines > MAX_LINES) {
			throw ApiException.conflict("CART_FULL", "Your cart can hold up to " + MAX_LINES + " different products.");
		}
		targets.forEach(this::checkPurchasable);
		targets.forEach(cart::put);
		return toView(cart);
	}

	public CartView setQuantity(Long userId, Long productId, int quantity) {
		Cart cart = carts.findByUserId(userId).orElseThrow(CartService::itemNotFound);
		int current = cart.item(productId).orElseThrow(CartService::itemNotFound).getQuantity();
		if (quantity > current) {
			checkPurchasable(productId, quantity); // reducing a quantity is always allowed
		}
		cart.put(productId, quantity);
		return toView(cart);
	}

	public CartView removeItem(Long userId, Long productId) {
		Cart cart = carts.findByUserId(userId).orElseThrow(CartService::itemNotFound);
		cart.item(productId).orElseThrow(CartService::itemNotFound);
		cart.remove(productId);
		return toView(cart);
	}

	public void clear(Long userId) {
		carts.findByUserId(userId).ifPresent(Cart::clear);
	}

	private void checkPurchasable(Long productId, int quantity) {
		if (quantity > MAX_LINE_QUANTITY) {
			throw ApiException.badRequest("QUANTITY_LIMIT", "You can buy at most " + MAX_LINE_QUANTITY + " of an item.");
		}
		ProductSummary product = catalog.findById(productId)
			.orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist."));
		if (!product.active()) {
			throw ApiException.conflict("PRODUCT_UNAVAILABLE", product.name() + " is no longer available.");
		}
		if (product.availableQuantity() < quantity) {
			throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
					product.availableQuantity() == 0 ? product.name() + " is out of stock."
							: "Only " + product.availableQuantity() + " of " + product.name() + " left in stock.")
				.with("available", product.availableQuantity());
		}
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
		List<CartView.Item> items = lines.stream().map(line -> {
			ProductSummary p = products.get(line.getProductId());
			BigDecimal unitPrice = p.price();
			return new CartView.Item(p.id(), p.sku(), p.slug(), p.name(), p.brand(), p.categorySlug(), unitPrice,
					line.getQuantity(), PricingPolicy.money(unitPrice.multiply(BigDecimal.valueOf(line.getQuantity()))),
					p.availableQuantity(), p.canFulfil(line.getQuantity()));
		}).toList();
		BigDecimal subtotal = items.stream().map(CartView.Item::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		PriceBreakdown price = pricing.quote(subtotal);
		int itemCount = items.stream().mapToInt(CartView.Item::quantity).sum();
		boolean ready = !items.isEmpty() && items.stream().allMatch(CartView.Item::available);
		return new CartView(items, itemCount, price.subtotal(), price.shipping(), price.tax(), price.total(),
				price.freeShippingThreshold(), ready);
	}

	private static ApiException itemNotFound() {
		return ApiException.notFound("CART_ITEM_NOT_FOUND", "This product is not in your cart.");
	}
}
