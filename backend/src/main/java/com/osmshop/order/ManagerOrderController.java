package com.osmshop.order;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.order.FulfillmentDtos.AssignRequest;
import com.osmshop.order.FulfillmentDtos.DeliveryStaffOption;
import com.osmshop.order.FulfillmentDtos.StaffOrderSummary;
import com.osmshop.order.FulfillmentDtos.StaffOrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Requires MANAGER_ORDER_VIEW / MANAGER_ORDER_ASSIGN (see SecurityConfig). */
@Validated
@RestController
@RequestMapping("/api/manager")
public class ManagerOrderController {
    private final ManagerOrderService orders;

    public ManagerOrderController(ManagerOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public PageResponse<StaffOrderSummary> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.list(status, search, page, size);
    }

    @GetMapping("/orders/{id}")
    public StaffOrderView get(@PathVariable long id) {
        return orders.get(id);
    }

    @GetMapping("/delivery-staff")
    public List<DeliveryStaffOption> deliveryStaff() {
        return orders.deliveryStaff();
    }

    @PostMapping("/orders/{id}/assign")
    public StaffOrderView assign(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                 @Valid @RequestBody AssignRequest request) {
        return orders.assign(user.id(), id, request.deliveryStaffId());
    }
}
