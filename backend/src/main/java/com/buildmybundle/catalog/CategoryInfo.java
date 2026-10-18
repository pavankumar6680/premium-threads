package com.buildmybundle.catalog;

/**
 * An active category with its department (parent); {@code leaf} categories are the ones products belong to.
 *
 * @param outfitSlot TOP, BOTTOM, FULL, LAYER or ACCESSORY (null for departments)
 * @param sizeScale ALPHA, WAIST or ONE_SIZE (null for departments)
 */
public record CategoryInfo(Long id, String slug, String name, String departmentSlug, String departmentName,
		boolean leaf, String outfitSlot, String sizeScale) {
}
