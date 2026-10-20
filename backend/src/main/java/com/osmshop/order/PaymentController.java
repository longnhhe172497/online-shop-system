package com.osmshop.order;

import com.osmshop.auth.AuthPrincipal;
import com.osmshop.order.OrderDtos.OrderDetail;
import com.osmshop.order.OrderDtos.SimulateRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Development simulation of a QR payment result; no real money is transferred. */
@RestController
@RequestMapping("/api/payment-attempts")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{id}/simulate")
    public OrderDetail simulate(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                @Valid @RequestBody SimulateRequest request) {
        return payments.simulate(user.id(), id, request.outcome());
    }
}
