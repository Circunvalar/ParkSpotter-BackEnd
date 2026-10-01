package com.ucentral.desarrollos.backendparkspotter.shared.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados con un formato JSON estable para web y Android
 * (no se expone directamente el Page de Spring).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages,
                page == 0, page >= totalPages - 1);
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return of(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
