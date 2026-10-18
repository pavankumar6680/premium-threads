package com.buildmybundle.bundles.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A set of products for a goal. RECOMMENDED bundles belong to a recommendation run and never change;
 * customers edit DRAFT copies and SAVE them. {@code @Version} turns concurrent edits into a 409.
 */
@Entity
@Table(name = "bundles")
public class Bundle {

	public static final String RECOMMENDED = "RECOMMENDED";
	public static final String DRAFT = "DRAFT";
	public static final String SAVED = "SAVED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "goal_id", nullable = false, updatable = false)
	private UUID goalId;

	@Column(name = "run_id", updatable = false)
	private UUID runId;

	@Column(name = "source_bundle_id", updatable = false)
	private UUID sourceBundleId;

	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false, length = 10)
	private String tier;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 12)
	private String status;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal budget;

	@Column(name = "total_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalPrice;

	@Column(precision = 10, scale = 4)
	private BigDecimal score;

	@Column(length = 500)
	private String summary;

	@OneToMany(mappedBy = "bundle", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder")
	private List<BundleItem> items = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Bundle() {
	}

	public Bundle(UUID goalId, UUID runId, Long userId, String tier, String name, String status, BigDecimal budget,
			BigDecimal totalPrice, BigDecimal score, String summary) {
		this.goalId = goalId;
		this.runId = runId;
		this.userId = userId;
		this.tier = tier;
		this.name = name;
		this.status = status;
		this.budget = budget;
		this.totalPrice = totalPrice;
		this.score = score;
		this.summary = summary;
	}

	/** An editable copy of this bundle for {@code owner}. */
	public Bundle draftCopy(Long owner) {
		Bundle copy = new Bundle(goalId, null, owner, tier, name, DRAFT, budget, totalPrice, score, summary);
		copy.sourceBundleId = id;
		items.forEach(i -> copy.addItem(i.getProductId(), i.getCategoryId(), i.getQuantity(), i.getUnitPrice(),
				i.isOptional(), i.getExplanation()).chooseVariant(i.getVariantId()));
		return copy;
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

	public BundleItem addItem(Long productId, Long categoryId, int quantity, BigDecimal unitPrice, boolean optional,
			String explanation) {
		int sortOrder = items.stream().mapToInt(BundleItem::getSortOrder).max().orElse(-1) + 1;
		BundleItem item = new BundleItem(this, productId, categoryId, quantity, unitPrice, optional, explanation, sortOrder);
		items.add(item);
		touch();
		return item;
	}

	public Optional<BundleItem> item(Long categoryId) {
		return items.stream().filter(i -> i.getCategoryId().equals(categoryId)).findFirst();
	}

	public void removeItem(Long categoryId) {
		items.removeIf(i -> i.getCategoryId().equals(categoryId));
		touch();
	}

	public void setBudget(BigDecimal budget) {
		this.budget = budget;
		touch();
	}

	public void updateTotals(BigDecimal totalPrice, String summary) {
		this.totalPrice = totalPrice;
		if (summary != null) {
			this.summary = summary;
		}
		touch();
	}

	public void save(String name) {
		this.name = name;
		this.status = SAVED;
		touch();
	}

	public boolean editable() {
		return !RECOMMENDED.equals(status);
	}

	/** Ensures the row (and its version) changes on every edit, even when only items changed. */
	public void touch() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getGoalId() {
		return goalId;
	}

	public UUID getRunId() {
		return runId;
	}

	public UUID getSourceBundleId() {
		return sourceBundleId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getTier() {
		return tier;
	}

	public String getName() {
		return name;
	}

	public String getStatus() {
		return status;
	}

	public BigDecimal getBudget() {
		return budget;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public BigDecimal getScore() {
		return score;
	}

	public String getSummary() {
		return summary;
	}

	public List<BundleItem> getItems() {
		return items;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
