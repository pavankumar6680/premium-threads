package com.buildmybundle.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Stable JSON envelope for paginated results (independent of Spring Data's Page serialization). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static final int MAX_PAGE_SIZE = 60;

	public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
		int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
		return new PageResponse<>(content, page, size, totalElements, totalPages);
	}

	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return of(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements());
	}

	/** Clamps client-supplied paging parameters to sane bounds. */
	public static int clampSize(Integer size, int defaultSize) {
		if (size == null || size < 1) {
			return defaultSize;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}

	public static int clampPage(Integer page) {
		return page == null || page < 0 ? 0 : page;
	}
}
