package com.buildmybundle.cart;

/** A quantity of one variant (size and color) of a product. */
public record CartLine(Long productId, Long variantId, int quantity) {
}
