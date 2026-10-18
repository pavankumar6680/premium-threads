package com.buildmybundle.goals.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.buildmybundle.goals.GoalModels.Quality;
import com.buildmybundle.goals.GoalModels.RequirementKind;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A customer's shopping goal. Immutable after creation (no setters): adjusting a goal creates a new one,
 * so recommendations always refer to exactly the inputs they were computed from. The random UUID doubles
 * as an unguessable share link, which lets anonymous visitors build goals too.
 */
@Entity
@Table(name = "shopping_goals")
public class ShoppingGoal {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", updatable = false)
	private Long userId;

	@Column(name = "template_id", updatable = false)
	private Long templateId;

	@Column(nullable = false, length = 120, updatable = false)
	private String title;

	@Column(name = "raw_text", length = 2000, updatable = false)
	private String rawText;

	@Column(nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal budget;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10, updatable = false)
	private Quality quality;

	@Column(name = "space_width_cm", precision = 7, scale = 1, updatable = false)
	private BigDecimal spaceWidthCm;

	@Column(name = "space_depth_cm", precision = 7, scale = 1, updatable = false)
	private BigDecimal spaceDepthCm;

	@Column(length = 500, updatable = false)
	private String notes;

	/** MEN or WOMEN, null when the outfit is not for one wardrobe */
	@Column(length = 10, updatable = false)
	private String gender;

	@Column(name = "size_alpha", length = 12, updatable = false)
	private String sizeAlpha;

	@Column(name = "size_waist", length = 12, updatable = false)
	private String sizeWaist;

	@ElementCollection
	@CollectionTable(name = "goal_preferred_colors", joinColumns = @JoinColumn(name = "goal_id"))
	@Column(name = "color", length = 40, nullable = false)
	private Set<String> preferredColors = new LinkedHashSet<>();

	@OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder")
	private List<GoalRequirement> requirements = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "goal_preferred_brands", joinColumns = @JoinColumn(name = "goal_id"))
	@Column(name = "brand", length = 80, nullable = false)
	private Set<String> preferredBrands = new LinkedHashSet<>();

	@ElementCollection
	@CollectionTable(name = "goal_usage_tags", joinColumns = @JoinColumn(name = "goal_id"))
	@Column(name = "tag", length = 40, nullable = false)
	private Set<String> usageTags = new LinkedHashSet<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ShoppingGoal() {
	}

	public ShoppingGoal(Long userId, Long templateId, String title, String rawText, BigDecimal budget,
			Quality quality, BigDecimal spaceWidthCm, BigDecimal spaceDepthCm, String notes, Set<String> preferredBrands,
			Set<String> usageTags) {
		this.userId = userId;
		this.templateId = templateId;
		this.title = title;
		this.rawText = rawText;
		this.budget = budget;
		this.quality = quality;
		this.spaceWidthCm = spaceWidthCm;
		this.spaceDepthCm = spaceDepthCm;
		this.notes = notes;
		this.preferredBrands.addAll(preferredBrands);
		this.usageTags.addAll(usageTags);
	}

	/** Who the outfit is for (set once, at creation). */
	public void describeWearer(String gender, String sizeAlpha, String sizeWaist, Set<String> colors) {
		this.gender = gender;
		this.sizeAlpha = sizeAlpha;
		this.sizeWaist = sizeWaist;
		this.preferredColors.addAll(colors);
	}

	public String getGender() {
		return gender;
	}

	public String getSizeAlpha() {
		return sizeAlpha;
	}

	public String getSizeWaist() {
		return sizeWaist;
	}

	public Set<String> getPreferredColors() {
		return preferredColors;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public void addRequirement(Long categoryId, RequirementKind kind, int quantity) {
		requirements.add(new GoalRequirement(this, categoryId, kind, quantity, requirements.size()));
	}

	public UUID getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getTemplateId() {
		return templateId;
	}

	public String getTitle() {
		return title;
	}

	public String getRawText() {
		return rawText;
	}

	public BigDecimal getBudget() {
		return budget;
	}

	public Quality getQuality() {
		return quality;
	}

	public BigDecimal getSpaceWidthCm() {
		return spaceWidthCm;
	}

	public BigDecimal getSpaceDepthCm() {
		return spaceDepthCm;
	}

	public String getNotes() {
		return notes;
	}

	public List<GoalRequirement> getRequirements() {
		return requirements;
	}

	public Set<String> getPreferredBrands() {
		return preferredBrands;
	}

	public Set<String> getUsageTags() {
		return usageTags;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
