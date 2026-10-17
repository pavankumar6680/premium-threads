package com.buildmybundle.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

	public enum Status {
		APPROVED, REFUNDED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false, updatable = false, unique = true)
	private Long orderId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 30)
	private String method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Column(nullable = false, length = 40, unique = true)
	private String reference;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Payment() {
	}

	public Payment(Long orderId, BigDecimal amount, String method, String reference) {
		this.orderId = orderId;
		this.amount = amount;
		this.method = method;
		this.reference = reference;
		this.status = Status.APPROVED;
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

	public void refund() {
		status = Status.REFUNDED;
	}

	public Long getOrderId() {
		return orderId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getMethod() {
		return method;
	}

	public Status getStatus() {
		return status;
	}

	public String getReference() {
		return reference;
	}
}
