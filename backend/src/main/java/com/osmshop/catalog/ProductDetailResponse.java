package com.osmshop.catalog;

import java.math.BigDecimal;

public record ProductDetailResponse(long id, String sku, String name, String description,
                                    String imageUrl, BigDecimal price, long categoryId, String categoryName,
                                    int availableQuantity, BigDecimal avgRating, long reviewCount) {
}