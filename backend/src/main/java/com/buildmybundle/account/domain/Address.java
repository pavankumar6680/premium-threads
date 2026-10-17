package com.buildmybundle.account.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "addresses")
public class Address {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "full_name", nullable = false, length = 120)
	private String fullName;

	@Column(nullable = false, length = 200)
	private String line1;

	@Column(length = 200)
	private String line2;

	@Column(nullable = false, length = 100)
	private String city;

	@Column(nullable = false, length = 100)
	private String state;

	@Column(name = "postal_code", nullable = false, length = 20)
	private String postalCode;

	@Column(nullable = false, length = 2)
	private String country;

	@Column(length = 30)
	private String phone;

	@Column(name = "is_default", nullable = false)
	private boolean defaultAddress;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Address() {
	}

	public Address(Long userId) {
		this.userId = userId;
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

	public void update(String fullName, String line1, String line2, String city, String state, String postalCode,
			String country, String phone) {
		this.fullName = fullName;
		this.line1 = line1;
		this.line2 = line2;
		this.city = city;
		this.state = state;
		this.postalCode = postalCode;
		this.country = country;
		this.phone = phone;
	}

	public void setDefaultAddress(boolean defaultAddress) {
		this.defaultAddress = defaultAddress;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getFullName() {
		return fullName;
	}

	public String getLine1() {
		return line1;
	}

	public String getLine2() {
		return line2;
	}

	public String getCity() {
		return city;
	}

	public String getState() {
		return state;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getCountry() {
		return country;
	}

	public String getPhone() {
		return phone;
	}

	public boolean isDefaultAddress() {
		return defaultAddress;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
