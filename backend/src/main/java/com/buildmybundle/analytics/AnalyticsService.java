package com.buildmybundle.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store analytics for the admin dashboard.
 *
 * <p>
 * A reporting read model: it aggregates directly over several modules' tables in SQL, read-only, instead of
 * calling each module and joining in memory. This is the one deliberate exception to "each module owns its
 * tables"; it never writes, and no module depends on it. Revenue counts orders that were not cancelled.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

	public static final int LOW_STOCK_THRESHOLD = 5;

	public record Summary(int days, Instant from, Sales sales, List<DailyPoint> daily, Map<String, Long> ordersByStatus,
			List<TopProduct> topProducts, Inventory inventory, Goals goals, Recommendations recommendations,
			Customers customers) {
	}

	public record Sales(BigDecimal revenue, BigDecimal previousRevenue, long orders, long cancelledOrders,
			BigDecimal averageOrderValue, long unitsSold) {
	}

	public record DailyPoint(LocalDate date, BigDecimal revenue, long orders) {
	}

	public record TopProduct(Long productId, String name, String sku, long units, BigDecimal revenue) {
	}

	public record LowStockProduct(Long productId, String name, String sku, int stock) {
	}

	public record Inventory(long activeProducts, long lowStock, long outOfStock, List<LowStockProduct> lowest) {
	}

	public record TemplateCount(String title, long goals) {
	}

	public record Goals(long created, long describedInOwnWords, BigDecimal averageBudget, List<TemplateCount> byTemplate) {
	}

	public record Recommendations(long runs, long infeasibleRuns, double infeasibleShare, long averageDurationMs,
			long p95DurationMs, long averageNodes, long aiOverviews, long savedBundles, long customizedBundles) {
	}

	public record Customers(long total, long newInPeriod) {
	}

	private final JdbcClient jdbc;

	public AnalyticsService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Summary summary(int days) {
		Instant now = Instant.now();
		Instant from = now.minus(days, ChronoUnit.DAYS);
		Instant previousFrom = from.minus(days, ChronoUnit.DAYS);
		return new Summary(days, from, sales(from, previousFrom), daily(days), ordersByStatus(from), topProducts(from),
				inventory(), goals(from), recommendations(from), customers(from));
	}

	private Sales sales(Instant from, Instant previousFrom) {
		record Totals(BigDecimal revenue, long orders, long cancelled) {
		}
		Totals t = jdbc.sql("""
				SELECT coalesce(sum(total) FILTER (WHERE status <> 'CANCELLED'), 0) AS revenue,
				       count(*) FILTER (WHERE status <> 'CANCELLED') AS orders,
				       count(*) FILTER (WHERE status = 'CANCELLED') AS cancelled
				FROM orders WHERE created_at >= :from
				""").param("from", ts(from))
			.query((rs, n) -> new Totals(rs.getBigDecimal("revenue"), rs.getLong("orders"), rs.getLong("cancelled")))
			.single();
		BigDecimal previous = jdbc.sql("""
				SELECT coalesce(sum(total), 0) FROM orders
				WHERE status <> 'CANCELLED' AND created_at >= :previousFrom AND created_at < :from
				""").param("previousFrom", ts(previousFrom)).param("from", ts(from)).query(BigDecimal.class).single();
		long units = jdbc.sql("""
				SELECT coalesce(sum(i.quantity), 0) FROM order_items i JOIN orders o ON o.id = i.order_id
				WHERE o.status <> 'CANCELLED' AND o.created_at >= :from
				""").param("from", ts(from)).query(Long.class).single();
		BigDecimal average = t.orders() == 0 ? BigDecimal.ZERO
				: t.revenue().divide(BigDecimal.valueOf(t.orders()), 2, RoundingMode.HALF_UP);
		return new Sales(t.revenue(), previous, t.orders(), t.cancelled(), average, units);
	}

	/** One point per calendar day (UTC), including days without orders. */
	private List<DailyPoint> daily(int days) {
		return jdbc.sql("""
				SELECT d::date AS day,
				       coalesce(sum(o.total) FILTER (WHERE o.status <> 'CANCELLED'), 0) AS revenue,
				       count(o.id) FILTER (WHERE o.status <> 'CANCELLED') AS orders
				FROM generate_series((now() AT TIME ZONE 'UTC')::date - (:days - 1), (now() AT TIME ZONE 'UTC')::date,
				                     interval '1 day') AS d
				LEFT JOIN orders o ON (o.created_at AT TIME ZONE 'UTC')::date = d::date
				GROUP BY d ORDER BY d
				""").param("days", days)
			.query((rs, n) -> new DailyPoint(rs.getDate("day").toLocalDate(), rs.getBigDecimal("revenue"), rs.getLong("orders")))
			.list();
	}

	private Map<String, Long> ordersByStatus(Instant from) {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (String status : List.of("PAID", "SHIPPED", "DELIVERED", "CANCELLED")) {
			counts.put(status, 0L);
		}
		jdbc.sql("SELECT status, count(*) AS n FROM orders WHERE created_at >= :from GROUP BY status")
			.param("from", ts(from))
			.query(rs -> {
				counts.put(rs.getString("status"), rs.getLong("n"));
			});
		return counts;
	}

	private List<TopProduct> topProducts(Instant from) {
		return jdbc.sql("""
				SELECT i.product_id, max(i.product_name) AS name, max(i.sku) AS sku, sum(i.quantity) AS units,
				       sum(i.line_total) AS revenue
				FROM order_items i JOIN orders o ON o.id = i.order_id
				WHERE o.status <> 'CANCELLED' AND o.created_at >= :from
				GROUP BY i.product_id ORDER BY units DESC, revenue DESC LIMIT 5
				""").param("from", ts(from))
			.query((rs, n) -> new TopProduct(rs.getLong("product_id"), rs.getString("name"), rs.getString("sku"),
					rs.getLong("units"), rs.getBigDecimal("revenue")))
			.list();
	}

	private Inventory inventory() {
		record Counts(long active, long low, long out) {
		}
		Counts c = jdbc.sql("""
				SELECT count(*) AS active,
				       count(*) FILTER (WHERE coalesce(i.quantity_on_hand, 0) <= :threshold) AS low,
				       count(*) FILTER (WHERE coalesce(i.quantity_on_hand, 0) = 0) AS out
				FROM products p LEFT JOIN inventory i ON i.product_id = p.id WHERE p.status = 'ACTIVE'
				""").param("threshold", LOW_STOCK_THRESHOLD)
			.query((rs, n) -> new Counts(rs.getLong("active"), rs.getLong("low"), rs.getLong("out")))
			.single();
		List<LowStockProduct> lowest = jdbc.sql("""
				SELECT p.id, p.name, p.sku, coalesce(i.quantity_on_hand, 0) AS stock
				FROM products p LEFT JOIN inventory i ON i.product_id = p.id
				WHERE p.status = 'ACTIVE' AND coalesce(i.quantity_on_hand, 0) <= :threshold
				ORDER BY stock, p.name LIMIT 8
				""").param("threshold", LOW_STOCK_THRESHOLD)
			.query((rs, n) -> new LowStockProduct(rs.getLong("id"), rs.getString("name"), rs.getString("sku"), rs.getInt("stock")))
			.list();
		return new Inventory(c.active(), c.low(), c.out(), lowest);
	}

	private Goals goals(Instant from) {
		record Counts(long created, long described, BigDecimal averageBudget) {
		}
		Counts c = jdbc.sql("""
				SELECT count(*) AS created, count(*) FILTER (WHERE raw_text IS NOT NULL AND raw_text <> '') AS described,
				       coalesce(round(avg(budget), 2), 0) AS average_budget
				FROM shopping_goals WHERE created_at >= :from
				""").param("from", ts(from))
			.query((rs, n) -> new Counts(rs.getLong("created"), rs.getLong("described"), rs.getBigDecimal("average_budget")))
			.single();
		List<TemplateCount> byTemplate = jdbc.sql("""
				SELECT coalesce(t.title, 'Custom goal') AS title, count(*) AS goals
				FROM shopping_goals g LEFT JOIN goal_templates t ON t.id = g.template_id
				WHERE g.created_at >= :from
				GROUP BY coalesce(t.title, 'Custom goal') ORDER BY goals DESC, title LIMIT 8
				""").param("from", ts(from))
			.query((rs, n) -> new TemplateCount(rs.getString("title"), rs.getLong("goals")))
			.list();
		return new Goals(c.created(), c.described(), c.averageBudget(), byTemplate);
	}

	private Recommendations recommendations(Instant from) {
		record Runs(long runs, long infeasible, long avgMs, long p95Ms, long avgNodes) {
		}
		Runs r = jdbc.sql("""
				SELECT count(*) AS runs, count(*) FILTER (WHERE status = 'INFEASIBLE') AS infeasible,
				       coalesce(round(avg(duration_ms)), 0) AS avg_ms,
				       coalesce(round(percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms)), 0) AS p95_ms,
				       coalesce(round(avg(nodes_explored)), 0) AS avg_nodes
				FROM recommendation_runs WHERE created_at >= :from
				""").param("from", ts(from))
			.query((rs, n) -> new Runs(rs.getLong("runs"), rs.getLong("infeasible"), rs.getLong("avg_ms"),
					rs.getLong("p95_ms"), rs.getLong("avg_nodes")))
			.single();
		long aiOverviews = jdbc.sql("""
				SELECT count(*) FROM recommendation_run_messages m JOIN recommendation_runs r ON r.id = m.run_id
				WHERE m.kind = 'AI_OVERVIEW' AND r.created_at >= :from
				""").param("from", ts(from)).query(Long.class).single();
		long saved = count("SELECT count(*) FROM bundles WHERE status = 'SAVED' AND updated_at >= :from", from);
		long customized = count("SELECT count(*) FROM bundles WHERE source_bundle_id IS NOT NULL AND created_at >= :from", from);
		double share = r.runs() == 0 ? 0 : Math.round(1000.0 * r.infeasible() / r.runs()) / 1000.0;
		return new Recommendations(r.runs(), r.infeasible(), share, r.avgMs(), r.p95Ms(), r.avgNodes(), aiOverviews,
				saved, customized);
	}

	private Customers customers(Instant from) {
		String customers = """
				SELECT count(*) FROM users u
				WHERE EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
				              WHERE ur.user_id = u.id AND r.name = 'CUSTOMER')
				""";
		long total = jdbc.sql(customers).query(Long.class).single();
		long recent = jdbc.sql(customers + " AND u.created_at >= :from").param("from", ts(from)).query(Long.class).single();
		return new Customers(total, recent);
	}

	private long count(String sql, Instant from) {
		return jdbc.sql(sql).param("from", ts(from)).query(Long.class).single();
	}

	private static java.sql.Timestamp ts(Instant instant) {
		return java.sql.Timestamp.from(instant);
	}
}
