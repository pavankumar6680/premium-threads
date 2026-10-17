package com.buildmybundle.bundles.domain;

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

/** One category slot of a bundle. A slot is updated in place when its product changes. */
@Entity
@Table(name = "bundle_items")
public class BundleItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "bundle_id", nullable = false)
	private Bundle bundle;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "category_id", nullable = false)
	private Long categoryId;

	@Column(nullable = false)
	private int quantity;

	/** Price when the item was last chosen or refreshed; views also show the live price. */
	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	@Column(nullable = false)
	private boolean optional;

	@Column(nullable = false)
	private boolean locked;

	@Column(length = 1000)
	private String explanation;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected BundleItem() {
	}

	BundleItem(Bundle bundle, Long productId, Long categoryId, int quantity, BigDecimal unitPrice, boolean optional,
			String explanation, int sortOrder) {
		this.bundle = bundle;
		this.productId = productId;
		this.categoryId = categoryId;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.optional = optional;
		this.explanation = explanation;
		this.sortOrder = sortOrder;
	}

	public void replaceProduct(Long productId, BigDecimal unitPrice, String explanation) {
		this.productId = productId;
		this.unitPrice = unitPrice;
		this.explanation = explanation;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	public void refreshPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public Long getProductId() {
		return productId;
	}

	public Long getCategoryId() {
		return categoryId;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public boolean isOptional() {
		return optional;
	}

	public boolean isLocked() {
		return locked;
	}

	public String getExplanation() {
		return explanation;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
