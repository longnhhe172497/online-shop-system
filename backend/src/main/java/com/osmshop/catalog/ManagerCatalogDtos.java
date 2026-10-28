package com.osmshop.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Request and response shapes for the Manager catalog screens. Stock is read-only here (Warehouse owns it). */
public final class ManagerCatalogDtos {
    public static final String PRODUCT_STATUSES = "DRAFT|ACTIVE|INACTIVE|ARCHIVED";
    public static final String CATEGORY_STATUSES = "ACTIVE|INACTIVE|ARCHIVED";

    private ManagerCatalogDtos() {}

    /** Full product details; stock fields are deliberately absent. Blank imageUrl/description mean none. */
    public record ProductRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$") String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            @Size(max = 500) @Pattern(regexp = "^$|^(https?://|/)\\S+$") String imageUrl,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal price,
            @NotNull @Positive Long categoryId) {}

    public record ProductStatusRequest(@NotBlank @Pattern(regexp = PRODUCT_STATUSES) String status) {}

    public record CategoryRequest(@NotBlank @Size(max = 150) String name, @Size(max = 2000) String description) {}

    public record CategoryStatusRequest(@NotBlank @Pattern(regexp = CATEGORY_STATUSES) String status) {}

    public record ManagerProductView(long id, String sku, String name, String description, String imageUrl,
                                     BigDecimal price, long categoryId, String categoryName, String categoryStatus,
                                     String status, int stockOnHand, int stockReserved, int availableQuantity,
                                     OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record ManagerCategoryView(long id, String name, String description, String status,
                                      long productCount, long activeProductCount,
                                      OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    /** Choices for the product form's category dropdown (archived categories are left out). */
    public record CategoryOption(long id, String name, String status) {}
}
