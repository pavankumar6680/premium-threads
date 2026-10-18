package com.buildmybundle.cart.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One cart per user. Rows are created with an upsert (see CartRepository), never via persist. */
@Entity
@Table(name = "carts")
public class Cart {

	@Id
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false, insertable = false)
	private Long userId;

	@OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("addedAt ASC, id ASC")
	private List<CartItem> items = new ArrayList<>();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	// Bumped on every change (touch), so concurrent edits of the same cart fail fast instead of losing updates.
	@Version
	private long version;

	protected Cart() {
	}

	/** The line for a variant (one line per size and color). */
	public Optional<CartItem> item(Long variantId) {
		return items.stream().filter(i -> i.getVariantId().equals(variantId)).findFirst();
	}

	public void put(Long productId, Long variantId, int quantity) {
		item(variantId).ifPresentOrElse(i -> i.setQuantity(quantity),
				() -> items.add(new CartItem(this, productId, variantId, quantity)));
		touch();
	}

	public void remove(Long variantId) {
		items.removeIf(i -> i.getVariantId().equals(variantId));
		touch();
	}

	public void clear() {
		items.clear();
		touch();
	}

	private void touch() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public List<CartItem> getItems() {
		return items;
	}
}
