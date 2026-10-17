package com.buildmybundle.inventory;

public record StockShortage(Long productId, int requested, int available) {
}
