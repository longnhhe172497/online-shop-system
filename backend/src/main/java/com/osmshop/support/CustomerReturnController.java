package com.osmshop.support;

import com.osmshop.auth.AuthPrincipal;
import com.osmshop.support.AfterSalesDtos.CreateReturnRequest;
import com.osmshop.support.AfterSalesDtos.ReturnView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerReturnController {
    private final AfterSalesService service;
    public CustomerReturnController(AfterSalesService service){this.service=service;}
    @GetMapping("/returns") public List<ReturnView> mine(@AuthenticationPrincipal AuthPrincipal user){return service.customerReturns(user.id());}
    @PostMapping("/{orderId}/returns") @ResponseStatus(HttpStatus.CREATED)
    public ReturnView create(@AuthenticationPrincipal AuthPrincipal user,@PathVariable long orderId,@Valid @RequestBody CreateReturnRequest request){
        return service.createReturn(user.id(),orderId,request);
    }
}
