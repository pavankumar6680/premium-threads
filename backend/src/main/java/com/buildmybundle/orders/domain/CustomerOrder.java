package com.buildmybundle.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.buildmybundle.pricing.PriceBreakdown;
import com.buildmybundle.pricing.PricingPolicy;
import com.buildmybundle.shared.error.ApiException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;

/** An order. Named CustomerOrder because ORDER is a reserved word in JPQL. */
@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class CustomerOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false, unique = true, length = 20, updatable = false)
	private String orderNumber;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal shipping;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal tax;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@Column(nullable = false, length = 3)
	private String currency = "USD";

	@Embedded
	private ShippingAddress shippingAddress;

	@Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
	private String idempotencyKey;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	@BatchSize(size = 50)
	private List<OrderItem> items = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Version
	private long version;

	protected CustomerOrder() {
	}

	public CustomerOrder(String orderNumber, Long userId, String idempotencyKey, ShippingAddress shippingAddress) {
		this.orderNumber = orderNumber;
		this.userId = userId;
		this.idempotencyKey = idempotencyKey;
		this.shippingAddress = shippingAddress;
		this.status = OrderStatus.PAID;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public void addItem(Long productId, String sku, String name, String brand, BigDecimal unitPrice, int quantity) {
		BigDecimal lineTotal = PricingPolicy.money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
		items.add(new OrderItem(this, productId, sku, name, brand, unitPrice, quantity, lineTotal));
	}

	public BigDecimal itemsSubtotal() {
		return items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public void applyTotals(PriceBreakdown price) {
		this.subtotal = price.subtotal();
		this.shipping = price.shipping();
		this.tax = price.tax();
		this.total = price.total();
	}

	public boolean isCancellable() {
		return status == OrderStatus.PAID;
	}

	public void cancel() {
		if (!isCancellable()) {
			throw ApiException.conflict("ORDER_NOT_CANCELLABLE",
					"Only orders that have not shipped yet can be cancelled.");
		}
		status = OrderStatus.CANCELLED;
		cancelledAt = Instant.now();
	}

	/** Staff action: PAID → SHIPPED. */
	public void ship() {
		if (status != OrderStatus.PAID) {
			throw invalidTransition(OrderStatus.SHIPPED);
		}
		status = OrderStatus.SHIPPED;
	}

	/** Staff action: SHIPPED → DELIVERED. */
	public void deliver() {
		if (status != OrderStatus.SHIPPED) {
			throw invalidTransition(OrderStatus.DELIVERED);
		}
		status = OrderStatus.DELIVERED;
	}

	private ApiException invalidTransition(OrderStatus target) {
		return ApiException.conflict("INVALID_ORDER_TRANSITION",
				"An order that is " + status.name().toLowerCase() + " cannot be marked " + target.name().toLowerCase() + ".")
			.with("status", status.name());
	}

	/** Product id → quantity, e.g. to reserve or release stock. */
	public Map<Long, Integer> quantities() {
		return items.stream().collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
	}

	public Long getId() {
		return id;
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public Long getUserId() {
		return userId;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getShipping() {
		return shipping;
	}

	public BigDecimal getTax() {
		return tax;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public String getCurrency() {
		return currency;
	}

	public ShippingAddress getShippingAddress() {
		return shippingAddress;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}
}
