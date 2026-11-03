package com.osmshop.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Product reviews (UC-CUS-015) and Manager moderation (UC-MNG-005). */
public final class ReviewDtos {
    public static final String REVIEW_STATUSES = "VISIBLE|HIDDEN";

    private ReviewDtos() {}

    public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 2000) String comment) {}

    /** status and reply are both optional, but at least one must be sent. A blank reply removes the reply. */
    public record ModerateRequest(@Pattern(regexp = REVIEW_STATUSES) String status, @Size(max = 1000) String reply) {}

    /** The customer's own review of one order item. status is shown so a hidden review is not a surprise. */
    public record MyReview(long id, long orderItemId, long productId, int rating, String comment, String status,
                           String managerReply, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    /** Public view: the author name is shortened (e.g. "Phạm Đ.") and no email or order data is exposed. */
    public record PublicReview(long id, String authorName, int rating, String comment, String managerReply,
                               OffsetDateTime repliedAt, OffsetDateTime createdAt, boolean edited) {}

    public record ManagerReview(long id, long productId, String productName, String sku, long orderId,
                                String orderCode, String customerName, String customerEmail, int rating,
                                String comment, String status, String managerReply, OffsetDateTime repliedAt,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
