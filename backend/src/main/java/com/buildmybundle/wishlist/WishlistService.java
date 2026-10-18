package com.buildmybundle.wishlist;

import java.util.List;

import com.buildmybundle.catalog.ProductCatalog;
import com.buildmybundle.catalog.ProductCard;
import com.buildmybundle.shared.error.ApiException;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saved-for-later products. Add and remove are idempotent. */
@Service
@Transactional
public class WishlistService {

	static final int MAX_ITEMS = 200;

	private final JdbcClient jdbc;
	private final ProductCatalog catalog;

	public WishlistService(JdbcClient jdbc, ProductCatalog catalog) {
		this.jdbc = jdbc;
		this.catalog = catalog;
	}

	/** Most recently saved first. Discontinued products stay visible (with active=false) until removed. */
	@Transactional(readOnly = true)
	public List<ProductCard> list(Long userId) {
		List<Long> ids = jdbc.sql("SELECT product_id FROM wishlist_items WHERE user_id = :userId ORDER BY added_at DESC")
			.param("userId", userId)
			.query(Long.class)
			.list();
		return catalog.cards(ids);
	}

	public void add(Long userId, Long productId) {
		catalog.findById(productId)
			.orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist."));
		long count = jdbc.sql("SELECT count(*) FROM wishlist_items WHERE user_id = :userId")
			.param("userId", userId)
			.query(Long.class)
			.single();
		if (count >= MAX_ITEMS) {
			throw ApiException.conflict("WISHLIST_FULL", "Your wishlist can hold up to " + MAX_ITEMS + " products.");
		}
		jdbc.sql("""
				INSERT INTO wishlist_items (user_id, product_id) VALUES (:userId, :productId)
				ON CONFLICT (user_id, product_id) DO NOTHING
				""").param("userId", userId).param("productId", productId).update();
	}

	public void remove(Long userId, Long productId) {
		jdbc.sql("DELETE FROM wishlist_items WHERE user_id = :userId AND product_id = :productId")
			.param("userId", userId)
			.param("productId", productId)
			.update();
	}
}
