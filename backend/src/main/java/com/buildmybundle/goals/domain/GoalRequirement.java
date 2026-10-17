package com.buildmybundle.goals.domain;

import com.buildmybundle.goals.GoalModels.RequirementKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "goal_requirements")
public class GoalRequirement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "goal_id", nullable = false)
	private ShoppingGoal goal;

	@Column(name = "category_id", nullable = false, updatable = false)
	private Long categoryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10, updatable = false)
	private RequirementKind kind;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(name = "sort_order", nullable = false, updatable = false)
	private int sortOrder;

	protected GoalRequirement() {
	}

	GoalRequirement(ShoppingGoal goal, Long categoryId, RequirementKind kind, int quantity, int sortOrder) {
		this.goal = goal;
		this.categoryId = categoryId;
		this.kind = kind;
		this.quantity = quantity;
		this.sortOrder = sortOrder;
	}

	public Long getCategoryId() {
		return categoryId;
	}

	public RequirementKind getKind() {
		return kind;
	}

	public int getQuantity() {
		return quantity;
	}
}
