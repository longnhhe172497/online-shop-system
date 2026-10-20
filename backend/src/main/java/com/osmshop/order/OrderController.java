package com.osmshop.order;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.order.OrderDtos.CancelRequest;
import com.osmshop.order.OrderDtos.CheckoutPreview;
import com.osmshop.order.OrderDtos.OrderDetail;
import com.osmshop.order.OrderDtos.OrderSummary;
import com.osmshop.order.OrderDtos.PlaceOrderRequest;
import com.osmshop.order.OrderDtos.PreviewRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    private final PaymentService payments;

    public OrderController(OrderService orders, PaymentService payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @PostMapping("/preview")
    public CheckoutPreview preview(@AuthenticationPrincipal AuthPrincipal user,
                                   @Valid @RequestBody(required = false) PreviewRequest request) {
        return orders.preview(user.id(), request == null ? null : request.voucherCode());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetail place(@AuthenticationPrincipal AuthPrincipal user,
                             @Valid @RequestBody PlaceOrderRequest request) {
        return orders.placeOrder(user.id(), request);
    }

    @GetMapping
    public PageResponse<OrderSummary> list(@AuthenticationPrincipal AuthPrincipal user,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.listOrders(user.id(), page, size);
    }

    @GetMapping("/{id}")
    public OrderDetail get(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id) {
        return orders.getOrder(user.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderDetail cancel(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                              @Valid @RequestBody(required = false) CancelRequest request) {
        return orders.cancel(user.id(), id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/payment-attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetail retryPayment(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id) {
        return payments.retry(user.id(), id);
    }
}
