package com.osmshop.order;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.order.FulfillmentDtos.ConfirmRequest;
import com.osmshop.order.FulfillmentDtos.StaffOrderView;
import com.osmshop.order.FulfillmentDtos.SupportCancelRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Requires SUPPORT_ORDER_VIEW / SUPPORT_ORDER_CONFIRM (see SecurityConfig). */
@Validated
@RestController
@RequestMapping("/api/support/orders")
public class SupportOrderController {
    private final SupportOrderService orders;

    public SupportOrderController(SupportOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/pending")
    public PageResponse<StaffOrderView> pending(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.pending(page, size);
    }

    @PostMapping("/{id}/confirm")
    public StaffOrderView confirm(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                  @Valid @RequestBody(required = false) ConfirmRequest request) {
        return orders.confirm(user.id(), id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/cancel")
    public StaffOrderView cancel(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                 @Valid @RequestBody SupportCancelRequest request) {
        return orders.cancel(user.id(), id, request.reason());
    }
}
