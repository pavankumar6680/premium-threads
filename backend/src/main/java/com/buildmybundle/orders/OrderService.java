package com.buildmybundle.orders;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.buildmybundle.account.AddressBook;
import com.buildmybundle.account.AddressSnapshot;
import com.buildmybundle.cart.CartLine;
import com.buildmybundle.cart.CartService;
import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductSummary;
import com.buildmybundle.catalog.VariantSummary;
import com.buildmybundle.inventory.InventoryService;
import com.buildmybundle.orders.OrderViews.Item;
import com.buildmybundle.orders.OrderViews.OrderSummary;
import com.buildmybundle.orders.OrderViews.OrderView;
import com.buildmybundle.orders.domain.CustomerOrder;
import com.buildmybundle.orders.domain.OrderItem;
import com.buildmybundle.orders.domain.OrderRepository;
import com.buildmybundle.orders.domain.ShippingAddress;
import com.buildmybundle.payments.PaymentService;
import com.buildmybundle.pricing.PricingPolicy;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checkout and order history.
 *
 * <p>
 * Checkout is one transaction: snapshot the cart at current prices, compute totals with the pricing
 * policy, insert the order, atomically reserve stock, record the (simulated) payment and empty the cart.
 * Any failure (for example insufficient stock) rolls everything back.
 *
 * <p>
 * Idempotency: the client sends an Idempotency-Key per checkout attempt. A repeated key returns the
 * original order. Two concurrent requests with the same key race on the unique (user_id, idempotency_key)
 * constraint; the loser's transaction rolls back and it returns the winner's order.
 */
@Service
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int DEFAULT_PAGE_SIZE = 10;

	private final OrderRepository orders;
	private final CartService cart;
	private final ProductCatalog catalog;
	private final InventoryService inventory;
	private final AddressBook addressBook;
	private final PaymentService payments;
	private final PricingPolicy pricing;
	private final TransactionTemplate tx;
	private final TransactionTemplate readTx;

	public OrderService(OrderRepository orders, CartService cart, ProductCatalog catalog,
			InventoryService inventory, AddressBook addressBook, PaymentService payments, PricingPolicy pricing,
			PlatformTransactionManager transactionManager) {
		this.orders = orders;
		this.cart = cart;
		this.catalog = catalog;
		this.inventory = inventory;
		this.addressBook = addressBook;
		this.payments = payments;
		this.pricing = pricing;
		this.tx = new TransactionTemplate(transactionManager);
		this.readTx = new TransactionTemplate(transactionManager);
		this.readTx.setReadOnly(true);
	}

	public OrderView checkout(Long userId, String idempotencyKey, Long addressId) {
		if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
			throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED",
					"Send a unique Idempotency-Key header (8-64 letters, digits or dashes) with each checkout.");
		}
		Optional<OrderView> replay = findByKey(userId, idempotencyKey);
		if (replay.isPresent()) {
			return replay.get();
		}
		try {
			return tx.execute(status -> placeOrder(userId, idempotencyKey, addressId));
		}
		catch (DataIntegrityViolationException ex) {
			log.info("Concurrent checkout with the same idempotency key for user {}", userId);
			return findByKey(userId, idempotencyKey).orElseThrow(() -> ex);
		}
	}

	private OrderView placeOrder(Long userId, String idempotencyKey, Long addressId) {
		List<CartLine> lines = cart.lines(userId);
		if (lines.isEmpty()) {
			throw ApiException.badRequest("CART_EMPTY", "Your cart is empty.");
		}
		AddressSnapshot address = addressBook.find(userId, addressId)
			.orElseThrow(() -> ApiException.notFound("ADDRESS_NOT_FOUND", "Address not found."));

		Map<Long, ProductSummary> products = catalog.findByIds(lines.stream().map(CartLine::productId).toList());
		Map<Long, VariantSummary> variants = catalog.findVariants(lines.stream().map(CartLine::variantId).toList());
		List<Long> unavailable = lines.stream()
			.filter(l -> products.get(l.productId()) == null || !products.get(l.productId()).active()
					|| variants.get(l.variantId()) == null || !variants.get(l.variantId()).active())
			.map(CartLine::productId)
			.toList();
		if (!unavailable.isEmpty()) {
			throw ApiException.conflict("PRODUCT_UNAVAILABLE", "Some items in your cart are no longer available.")
				.with("productIds", unavailable);
		}

		CustomerOrder order = new CustomerOrder(newOrderNumber(), userId, idempotencyKey, shippingAddress(address));
		for (CartLine line : lines) {
			ProductSummary p = products.get(line.productId());
			VariantSummary v = variants.get(line.variantId());
			order.addItem(p.id(), v.id(), v.sku(), v.size(), v.color(), p.name(), p.brand(), p.price(), line.quantity());
		}
		order.applyTotals(pricing.quote(order.itemsSubtotal()));

		orders.saveAndFlush(order); // surfaces a duplicate idempotency key before stock is touched
		inventory.reserve(order.quantities());
		payments.charge(order.getId(), order.getTotal());
		cart.clear(userId);
		log.info("Order {} placed by user {} for {} {}", order.getOrderNumber(), userId, order.getTotal(),
				order.getCurrency());
		return view(order);
	}

	public PageResponse<OrderSummary> list(Long userId, Integer page, Integer size) {
		return readTx.execute(status -> PageResponse.from(
				orders.findByUserIdOrderByCreatedAtDescIdDesc(userId,
						PageRequest.of(PageResponse.clampPage(page), PageResponse.clampSize(size, DEFAULT_PAGE_SIZE))),
				OrderService::summary));
	}

	public OrderView get(Long userId, Long orderId) {
		return readTx.execute(status -> view(orders.findByIdAndUserId(orderId, userId).orElseThrow(OrderService::notFound)));
	}

	/** Cancels a not-yet-shipped order, returns its stock and refunds the payment. */
	public OrderView cancel(Long userId, Long orderId) {
		return tx.execute(status -> {
			CustomerOrder order = orders.findForUpdate(orderId, userId).orElseThrow(OrderService::notFound);
			order.cancel();
			inventory.release(order.quantities());
			payments.refund(order.getId());
			log.info("Order {} cancelled by user {}", order.getOrderNumber(), userId);
			return view(order);
		});
	}

	private Optional<OrderView> findByKey(Long userId, String idempotencyKey) {
		return readTx.execute(status -> orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey).map(this::view));
	}

	/** Customer-facing view of an order (also embedded in the staff view). */
	OrderView view(CustomerOrder order) {
		Map<Long, ProductSummary> current = catalog
			.findByIds(order.getItems().stream().map(OrderItem::getProductId).toList());
		List<Item> items = order.getItems()
			.stream()
			.map(i -> new Item(i.getProductId(), i.getVariantId(),
					Optional.ofNullable(current.get(i.getProductId())).map(ProductSummary::slug).orElse(null),
					i.getSku(), i.getProductName(), i.getBrand(), displaySize(i.getSize()), displayColor(i.getColor()),
					i.getUnitPrice(), i.getQuantity(), i.getLineTotal()))
			.toList();
		return new OrderView(order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getCreatedAt(),
				order.getCancelledAt(), items, order.getSubtotal(), order.getShipping(), order.getTax(),
				order.getTotal(), order.getCurrency(), order.getShippingAddress(),
				payments.findByOrder(order.getId()).orElse(null), order.isCancellable());
	}

	private static OrderSummary summary(CustomerOrder order) {
		List<OrderItem> items = order.getItems();
		return new OrderSummary(order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getCreatedAt(),
				order.getTotal(), order.getCurrency(), items.stream().mapToInt(OrderItem::getQuantity).sum(),
				items.stream().limit(3).map(OrderItem::getProductName).toList());
	}

	/** Default options of single-variant products are not worth showing. */
	private static String displaySize(String size) {
		return "One size".equals(size) ? null : size;
	}

	private static String displayColor(String color) {
		return "Standard".equals(color) ? null : color;
	}

	private static ShippingAddress shippingAddress(AddressSnapshot a) {
		return new ShippingAddress(a.fullName(), a.line1(), a.line2(), a.city(), a.state(), a.postalCode(),
				a.country(), a.phone());
	}

	private static String newOrderNumber() {
		StringBuilder sb = new StringBuilder("BMB-");
		for (int i = 0; i < 8; i++) {
			sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}

	private static ApiException notFound() {
		return ApiException.notFound("ORDER_NOT_FOUND", "Order not found.");
	}
}
