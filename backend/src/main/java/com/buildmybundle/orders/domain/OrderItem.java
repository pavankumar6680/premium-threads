package com.buildmybundle.orders.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Immutable line with the product name, SKU and price as they were at purchase time. */
@Entity
@Table(name = "order_items")
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private CustomerOrder order;

	@Column(name = "product_id", nullable = false, updatable = false)
	private Long productId;

	/** The purchased size and color; null only for order lines older than product variants. */
	@Column(name = "variant_id", updatable = false)
	private Long variantId;

	@Column(nullable = false, length = 40, updatable = false)
	private String sku;

	@Column(length = 12, updatable = false)
	private String size;

	@Column(length = 40, updatable = false)
	private String color;

	@Column(name = "product_name", nullable = false, length = 160, updatable = false)
	private String productName;

	@Column(nullable = false, length = 80, updatable = false)
	private String brand;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal unitPrice;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(name = "line_total", nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal lineTotal;

	protected OrderItem() {
	}

	OrderItem(CustomerOrder order, Long productId, Long variantId, String sku, String size, String color,
			String productName, String brand, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
		this.order = order;
		this.productId = productId;
		this.variantId = variantId;
		this.sku = sku;
		this.size = size;
		this.color = color;
		this.productName = productName;
		this.brand = brand;
		this.unitPrice = unitPrice;
		this.quantity = quantity;
		this.lineTotal = lineTotal;
	}

	public Long getProductId() {
		return productId;
	}

	public Long getVariantId() {
		return variantId;
	}

	public String getSize() {
		return size;
	}

	public String getColor() {
		return color;
	}

	public String getSku() {
		return sku;
	}

	public String getProductName() {
		return productName;
	}

	public String getBrand() {
		return brand;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getLineTotal() {
		return lineTotal;
	}
}
