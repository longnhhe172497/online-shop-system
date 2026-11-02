package com.osmshop.support;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.support.AfterSalesDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/tickets")
public class SupportTicketController {
    private final AfterSalesService service;
    public SupportTicketController(AfterSalesService service){this.service=service;}
    @GetMapping @PreAuthorize("hasRole('CUSTOMER')")
    public List<TicketView> mine(@AuthenticationPrincipal AuthPrincipal user){return service.customerTickets(user.id());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CUSTOMER')")
    public TicketView create(@AuthenticationPrincipal AuthPrincipal user,@Valid @RequestBody CreateTicketRequest request){return service.createTicket(user.id(),request);}
    @GetMapping("/queue") @PreAuthorize("hasAuthority('PERM_SUPPORT_TICKET_VIEW')")
    public PageResponse<TicketView> queue(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.ticketQueue(status,page,size);}
    @PostMapping("/{id}/messages") @PreAuthorize("hasRole('CUSTOMER') or hasAuthority('PERM_SUPPORT_TICKET_REPLY')")
    public TicketView reply(@AuthenticationPrincipal AuthPrincipal user,@PathVariable long id,@Valid @RequestBody TicketMessageRequest request){
        if("CUSTOMER".equals(user.role())) return service.customerReplyTicket(user.id(),id,request.message());
        if(!"SUPPORT".equals(user.role()) && !"ADMIN".equals(user.role())) throw new org.springframework.security.access.AccessDeniedException("Access denied");
        return service.replyTicket(user.id(),id,request.message());
    }
    @PatchMapping("/{id}/status") @PreAuthorize("hasAuthority('PERM_SUPPORT_TICKET_REPLY')")
    public TicketView status(@AuthenticationPrincipal AuthPrincipal user,@PathVariable long id,@Valid @RequestBody TicketStatusRequest request){return service.updateTicketStatus(user.id(),id,request.status());}
}
