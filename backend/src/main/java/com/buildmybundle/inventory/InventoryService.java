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
 * Stock levels per product variant (size × color). Reservation uses a conditional UPDATE
 * ({@code ... WHERE quantity_on_hand >= :qty}), which PostgreSQL executes atomically per row, so two
 * concurrent checkouts can never both take the last unit. No read-then-write race and no long-held locks.
 */
@Service
public class InventoryService {

	private final JdbcClient jdbc;

	public InventoryService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Units on hand per variant; variants without a stock row are reported as 0. */
	@Transactional(readOnly = true)
	public Map<Long, Integer> availableQuantities(Collection<Long> variantIds) {
		if (variantIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Integer> found = jdbc
			.sql("SELECT variant_id, quantity_on_hand FROM variant_stock WHERE variant_id IN (:ids)")
			.param("ids", variantIds)
			.query((rs, n) -> Map.entry(rs.getLong("variant_id"), rs.getInt("quantity_on_hand")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		return variantIds.stream().distinct().collect(Collectors.toMap(id -> id, id -> found.getOrDefault(id, 0)));
	}

	@Transactional(readOnly = true)
	public int availableQuantity(Long variantId) {
		return availableQuantities(List.of(variantId)).get(variantId);
	}

	/**
	 * Decrements stock for every variant line or fails with {@link InsufficientStockException} listing all
	 * shortages. Must run inside the caller's transaction so a failure rolls back the lines already
	 * decremented. Rows are updated in variant-id order so concurrent multi-item checkouts lock rows in the
	 * same order and cannot deadlock.
	 */
	@Transactional
	public void reserve(Map<Long, Integer> quantities) {
		List<StockShortage> shortages = new ArrayList<>();
		for (Map.Entry<Long, Integer> line : new TreeMap<>(quantities).entrySet()) {
			int updated = jdbc.sql("""
					UPDATE variant_stock
					SET quantity_on_hand = quantity_on_hand - :qty, updated_at = now()
					WHERE variant_id = :id AND quantity_on_hand >= :qty
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
		new TreeMap<>(quantities).forEach((variantId, qty) -> jdbc.sql("""
				INSERT INTO variant_stock (variant_id, quantity_on_hand) VALUES (:id, :qty)
				ON CONFLICT (variant_id) DO UPDATE
				SET quantity_on_hand = variant_stock.quantity_on_hand + :qty, updated_at = now()
				""").param("qty", qty).param("id", variantId).update());
	}

	/** Sets the absolute stock level of a variant (admin adjustments, tests). */
	@Transactional
	public void setStock(Long variantId, int quantity) {
		if (quantity < 0) {
			throw new IllegalArgumentException("Stock cannot be negative");
		}
		jdbc.sql("""
				INSERT INTO variant_stock (variant_id, quantity_on_hand) VALUES (:id, :qty)
				ON CONFLICT (variant_id) DO UPDATE SET quantity_on_hand = :qty, updated_at = now()
				""").param("id", variantId).param("qty", quantity).update();
	}
}
