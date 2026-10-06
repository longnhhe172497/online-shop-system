package com.osmshop.catalog;

import java.math.BigDecimal;

public record ProductResponse(long id, String sku, String name, String description,
                              String imageUrl, BigDecimal price, String categoryName,
                              int availableQuantity) {
}
