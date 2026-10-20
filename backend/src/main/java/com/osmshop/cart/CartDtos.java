package com.osmshop.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {
    public static final int MAX_LINE_QUANTITY = 99;

    private CartDtos() {}

    public record AddItemRequest(@NotNull @Positive Long productId,
                                 @NotNull @Min(1) @Max(MAX_LINE_QUANTITY) Integer quantity) {}

    public record UpdateItemRequest(@NotNull @Min(1) @Max(MAX_LINE_QUANTITY) Integer quantity) {}

    /** warning is null, PRODUCT_UNAVAILABLE or INSUFFICIENT_STOCK (same codes as checkout). */
    public record CartLine(long id, long productId, String sku, String name, String imageUrl,
                           BigDecimal unitPrice, int quantity, int availableQuantity,
                           BigDecimal lineTotal, String warning) {}

    /** subtotal counts only lines without a warning. */
    public record CartView(List<CartLine> items, int itemCount, BigDecimal subtotal) {}
}