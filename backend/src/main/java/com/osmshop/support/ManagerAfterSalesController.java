package com.osmshop.support;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.support.AfterSalesDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Operational Manager after-sales endpoints. Return decisions and refund completion live under
 * /api/manager; refunds for failed prepaid deliveries live under /api/after-sales so that they are
 * protected by method security without changing the shared SecurityConfig.
 */
@RestController
public class ManagerAfterSalesController {
    private final AfterSalesService service;
    public ManagerAfterSalesController(AfterSalesService service){this.service=service;}
    @GetMapping("/api/manager/returns") @PreAuthorize("hasAuthority('PERM_MANAGER_RETURN_DECIDE')")
    public PageResponse<ReturnView> returns(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.returns(status,page,size);}
    @PostMapping("/api/manager/returns/{id}/decision") @PreAuthorize("hasAuthority('PERM_MANAGER_RETURN_DECIDE')")
    public ReturnView decide(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable long id,@Valid @RequestBody ReturnDecisionRequest request){return service.decideReturn(actor.id(),id,request);}
    @GetMapping("/api/manager/refunds") @PreAuthorize("hasAuthority('PERM_MANAGER_REFUND_COMPLETE')")
    public PageResponse<RefundView> refunds(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.refunds(status,page,size);}
    @PostMapping("/api/manager/refunds/{id}/complete") @PreAuthorize("hasAuthority('PERM_MANAGER_REFUND_COMPLETE')")
    public RefundView complete(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable long id){return service.completeRefund(actor.id(),id);}
    @GetMapping("/api/after-sales/failed-deliveries") @PreAuthorize("hasAuthority('PERM_MANAGER_REFUND_COMPLETE')")
    public List<FailedDeliveryOrderView> failedDeliveries(){return service.failedDeliveryRefundCandidates();}
    @PostMapping("/api/after-sales/failed-deliveries/{orderId}/refund") @PreAuthorize("hasAuthority('PERM_MANAGER_REFUND_COMPLETE')")
    public RefundView createFailedDeliveryRefund(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable long orderId){return service.createFailedDeliveryRefund(actor.id(),orderId);}
}
