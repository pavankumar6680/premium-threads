package com.buildmybundle.cart.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cart_items")
public class CartItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cart_id", nullable = false)
	private Cart cart;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "added_at", nullable = false, updatable = false)
	private Instant addedAt;

	protected CartItem() {
	}

	CartItem(Cart cart, Long productId, int quantity) {
		this.cart = cart;
		this.productId = productId;
		this.quantity = quantity;
		this.addedAt = Instant.now();
	}

	void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public Long getProductId() {
		return productId;
	}

	public int getQuantity() {
		return quantity;
	}
}
