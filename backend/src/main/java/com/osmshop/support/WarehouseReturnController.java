package com.osmshop.support;

import com.osmshop.auth.AuthPrincipal;
import com.osmshop.api.PageResponse;
import com.osmshop.support.AfterSalesDtos.InspectionRequest;
import com.osmshop.support.AfterSalesDtos.ReturnView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouse/returns")
@PreAuthorize("hasAuthority('PERM_WAREHOUSE_RETURN_INSPECT')")
public class WarehouseReturnController {
    private final AfterSalesService service;
    public WarehouseReturnController(AfterSalesService service){this.service=service;}
    @GetMapping
    public PageResponse<ReturnView> queue(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return service.returns("RETURN_AUTHORIZED",page,size);
    }
    @PostMapping("/{id}/inspection")
    public ReturnView inspect(@AuthenticationPrincipal AuthPrincipal actor,@PathVariable long id,@Valid @RequestBody InspectionRequest request){
        return service.inspectReturn(actor.id(),id,request);
    }
}
