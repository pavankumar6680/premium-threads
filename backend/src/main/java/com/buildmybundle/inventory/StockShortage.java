package com.buildmybundle.inventory;

/** A variant that cannot supply the requested quantity. */
public record StockShortage(Long variantId, int requested, int available) {
}
