package com.buildmybundle.catalog;

/** A category with its department (parent); {@code leaf} categories are the ones products belong to. */
public record CategoryInfo(Long id, String slug, String name, String departmentSlug, String departmentName,
		boolean leaf) {
}
