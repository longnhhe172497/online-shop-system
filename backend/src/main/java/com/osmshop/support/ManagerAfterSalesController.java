package com.osmshop.support;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.support.AfterSalesDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager")
public class ManagerAfterSalesController {
    private final AfterSalesService service;
    public ManagerAfterSalesController(AfterSalesService service){this.service=service;}
    @GetMapping("/returns") @PreAuthorize("hasAuthority('PERM_MANAGER_RETURN_DECIDE')")
    public PageResponse<ReturnView> returns(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.returns(status,page,size);}
    @PostMapping("/returns/{id}/decision") @PreAuthorize("hasAuthority('PERM_MANAGER_RETURN_DECIDE')")
    public ReturnView decide(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable long id,@Valid @RequestBody ReturnDecisionRequest request){return service.decideReturn(actor.id(),id,request);}
    @GetMapping("/refunds") @PreAuthorize("hasAuthority('PERM_MANAGER_REFUND_COMPLETE')")
    public PageResponse<RefundView> refunds(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.refunds(status,page,size);}
    @PostMapping("/refunds/{id}/complete") @PreAuthorize("hasAuthority('PERM_MANAGER_REFUND_COMPLETE')")
    public RefundView complete(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable long id){return service.completeRefund(actor.id(),id);}
}
