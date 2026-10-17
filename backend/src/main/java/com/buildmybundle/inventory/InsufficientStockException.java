package com.buildmybundle.inventory;

import java.util.List;

import com.buildmybundle.shared.error.ApiException;

import org.springframework.http.HttpStatus;

/** 409 INSUFFICIENT_STOCK; the problem body lists every short line under {@code shortages}. */
public class InsufficientStockException extends ApiException {

	private final transient List<StockShortage> shortages;

	public InsufficientStockException(List<StockShortage> shortages) {
		super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Some items do not have enough stock.");
		this.shortages = List.copyOf(shortages);
		with("shortages", this.shortages);
	}

	public List<StockShortage> getShortages() {
		return shortages;
	}
}
