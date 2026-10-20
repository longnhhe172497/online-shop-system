package com.osmshop.catalog;

import java.math.BigDecimal;

/** Optional storefront filters. Null fields mean "no filter". */
public record ProductFilter(String q, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, String sort) {
    public static ProductFilter none() {
        return new ProductFilter(null, null, null, null, null);
    }
}