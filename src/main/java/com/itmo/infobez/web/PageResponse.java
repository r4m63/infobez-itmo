package com.itmo.infobez.web;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
