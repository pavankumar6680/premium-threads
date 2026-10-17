package com.buildmybundle.orders.domain;

/** PAID → SHIPPED → DELIVERED; PAID → CANCELLED (by the customer, before shipping). */
public enum OrderStatus {
	PAID, SHIPPED, DELIVERED, CANCELLED
}
