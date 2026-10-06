package com.osmshop.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, long totalPages) {
    public PageResponse(List<T> items, int page, int size, long totalItems) {
        this(items, page, size, totalItems, (totalItems + size - 1) / size);
    }
}
