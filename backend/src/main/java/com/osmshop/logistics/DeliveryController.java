package com.osmshop.logistics;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.logistics.LogisticsDtos.DailyReportRequest;
import com.osmshop.logistics.LogisticsDtos.DailyReportView;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentListItem;
import com.osmshop.logistics.LogisticsDtos.UpdateShipmentStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/delivery")
@PreAuthorize("hasAnyRole('DELIVERY','ADMIN')")
public class DeliveryController {
    private final DeliveryService service;
    private final StaffDailyReportService reports;

    public DeliveryController(DeliveryService service, StaffDailyReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    @GetMapping("/shipments")
    public PageResponse<ShipmentListItem> shipments(@AuthenticationPrincipal AuthPrincipal actor,
            Authentication authentication,
            @RequestParam(defaultValue = "MINE") DeliveryService.Scope scope,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(actor.id(), isAdmin(authentication), scope, status, page, size);
    }

    @GetMapping("/shipments/{id}")
    public ShipmentDetailResponse shipment(@AuthenticationPrincipal AuthPrincipal actor,
            Authentication authentication, @PathVariable long id) {
        return service.get(actor.id(), isAdmin(authentication), id);
    }

    @PatchMapping("/shipments/{id}/status")
    public ShipmentDetailResponse updateStatus(@AuthenticationPrincipal AuthPrincipal actor,
            Authentication authentication, @PathVariable long id,
            @Valid @RequestBody UpdateShipmentStatusRequest request) {
        return service.updateStatus(actor.id(), isAdmin(authentication), id, request);
    }

    /** Nộp (upsert) báo cáo ngày của người dùng hiện tại. */
    @PostMapping("/reports/daily")
    public DailyReportView submitDailyReport(@AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody DailyReportRequest request) {
        return reports.submit(actor.id(), request.content());
    }

    /** 10 báo cáo gần nhất của nhân viên giao hàng. */
    @GetMapping("/reports/daily/recent")
    public List<DailyReportView> recentDailyReports(@AuthenticationPrincipal AuthPrincipal actor) {
        return reports.recent(actor.id(), "DELIVERY");
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
    }
}
