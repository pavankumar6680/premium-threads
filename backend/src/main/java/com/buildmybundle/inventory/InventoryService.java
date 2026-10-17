package com.buildmybundle.inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock levels. Reservation uses a conditional UPDATE ({@code ... WHERE quantity_on_hand >= :qty}), which
 * PostgreSQL executes atomically per row, so two concurrent checkouts can never both take the last unit.
 * No read-then-write race and no long-held locks.
 */
@Service
public class InventoryService {

	private final JdbcClient jdbc;

	public InventoryService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Units on hand per product; products without an inventory row are reported as 0. */
	@Transactional(readOnly = true)
	public Map<Long, Integer> availableQuantities(Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Integer> found = jdbc
			.sql("SELECT product_id, quantity_on_hand FROM inventory WHERE product_id IN (:ids)")
			.param("ids", productIds)
			.query((rs, n) -> Map.entry(rs.getLong("product_id"), rs.getInt("quantity_on_hand")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		return productIds.stream().distinct().collect(Collectors.toMap(id -> id, id -> found.getOrDefault(id, 0)));
	}

	@Transactional(readOnly = true)
	public int availableQuantity(Long productId) {
		return availableQuantities(List.of(productId)).get(productId);
	}

	/**
	 * Decrements stock for every line or fails with {@link InsufficientStockException} listing all shortages.
	 * Must run inside the caller's transaction so a failure rolls back the lines already decremented.
	 * Rows are updated in product-id order so concurrent multi-item checkouts lock rows in the same order
	 * and cannot deadlock.
	 */
	@Transactional
	public void reserve(Map<Long, Integer> quantities) {
		List<StockShortage> shortages = new ArrayList<>();
		for (Map.Entry<Long, Integer> line : new TreeMap<>(quantities).entrySet()) {
			int updated = jdbc.sql("""
					UPDATE inventory
					SET quantity_on_hand = quantity_on_hand - :qty, updated_at = now()
					WHERE product_id = :id AND quantity_on_hand >= :qty
					""").param("qty", line.getValue()).param("id", line.getKey()).update();
			if (updated == 0) {
				shortages.add(new StockShortage(line.getKey(), line.getValue(), availableQuantity(line.getKey())));
			}
		}
		if (!shortages.isEmpty()) {
			throw new InsufficientStockException(shortages);
		}
	}

	/** Returns stock, e.g. when an order is cancelled. */
	@Transactional
	public void release(Map<Long, Integer> quantities) {
		new TreeMap<>(quantities).forEach((productId, qty) -> jdbc.sql("""
				UPDATE inventory SET quantity_on_hand = quantity_on_hand + :qty, updated_at = now()
				WHERE product_id = :id
				""").param("qty", qty).param("id", productId).update());
	}

	/** Those of {@code productIds} whose stock is at or below {@code threshold} (no inventory row counts as 0). */
	@Transactional(readOnly = true)
	public List<Long> productIdsWithStockAtMost(Collection<Long> productIds, int threshold) {
		Map<Long, Integer> stock = availableQuantities(productIds);
		return productIds.stream().filter(id -> stock.getOrDefault(id, 0) <= threshold).toList();
	}

	/** Sets the absolute stock level (admin adjustments, tests). */
	@Transactional
	public void setStock(Long productId, int quantity) {
		if (quantity < 0) {
			throw new IllegalArgumentException("Stock cannot be negative");
		}
		jdbc.sql("""
				INSERT INTO inventory (product_id, quantity_on_hand) VALUES (:id, :qty)
				ON CONFLICT (product_id) DO UPDATE SET quantity_on_hand = :qty, updated_at = now()
				""").param("id", productId).param("qty", quantity).update();
	}
}
