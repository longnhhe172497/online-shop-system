package com.osmshop.catalog;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.catalog.ReviewDtos.ManagerReview;
import com.osmshop.catalog.ReviewDtos.ModerateRequest;
import com.osmshop.catalog.ReviewDtos.MyReview;
import com.osmshop.catalog.ReviewDtos.PublicReview;
import com.osmshop.catalog.ReviewDtos.ReviewRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access rules live in SecurityConfig: /api/products/** GET is public, /api/order-items/** is CUSTOMER only
 * and /api/manager/reviews/** needs MANAGER_REVIEW_MODERATE.
 */
@Validated
@RestController
public class ReviewController {
    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/products/{productId}/reviews")
    public PageResponse<PublicReview> productReviews(@PathVariable long productId,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return reviews.productReviews(productId, page, size);
    }

    @GetMapping("/api/order-items/{orderItemId}/review")
    public MyReview own(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long orderItemId) {
        return reviews.getOwn(user.id(), orderItemId);
    }

    @PostMapping("/api/order-items/{orderItemId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public MyReview create(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long orderItemId,
                           @Valid @RequestBody ReviewRequest request) {
        return reviews.create(user.id(), orderItemId, request);
    }

    @PatchMapping("/api/order-items/{orderItemId}/review")
    public MyReview update(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long orderItemId,
                           @Valid @RequestBody ReviewRequest request) {
        return reviews.update(user.id(), orderItemId, request);
    }

    @GetMapping("/api/manager/reviews")
    public PageResponse<ManagerReview> managerList(
            @RequestParam(required = false) @Pattern(regexp = ReviewDtos.REVIEW_STATUSES) String status,
            @RequestParam(required = false) @Min(1) @Max(5) Integer rating,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviews.managerList(status, rating, q, page, size);
    }

    @PatchMapping("/api/manager/reviews/{id}")
    public ManagerReview moderate(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                  @Valid @RequestBody ModerateRequest request) {
        return reviews.moderate(user.id(), id, request);
    }
}
