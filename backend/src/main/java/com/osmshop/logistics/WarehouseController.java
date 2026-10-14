package com.osmshop.logistics;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.logistics.LogisticsDtos.DailyReportRequest;
import com.osmshop.logistics.LogisticsDtos.DailyReportView;
import com.osmshop.logistics.LogisticsDtos.PackOrderRequest;
import com.osmshop.logistics.LogisticsDtos.PackingOrderView;
import com.osmshop.logistics.LogisticsDtos.ProductStockView;
import com.osmshop.logistics.LogisticsDtos.RestockRequest;
import com.osmshop.logistics.LogisticsDtos.RestockResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.TransactionView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/warehouse")
@PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
public class WarehouseController {
    private final WarehouseService service;
    private final StaffDailyReportService reports;

    public WarehouseController(WarehouseService service, StaffDailyReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    @GetMapping("/products")
    public PageResponse<ProductStockView> products(@RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.products(search, lowStockOnly, page, size);
    }

    @PostMapping("/products/{id}/restock")
    @ResponseStatus(HttpStatus.CREATED)
    public RestockResponse restock(@AuthenticationPrincipal AuthPrincipal actor, @PathVariable long id,
            @Valid @RequestBody RestockRequest request) {
        return service.restock(actor.id(), id, request);
    }

    @GetMapping("/products/{id}/transactions")
    public PageResponse<TransactionView> transactions(@PathVariable long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.transactions(id, page, size);
    }

    @GetMapping("/orders")
    public PageResponse<PackingOrderView> orders(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.packingOrders(page, size);
    }

    @PostMapping("/orders/{id}/pack")
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentDetailResponse pack(@AuthenticationPrincipal AuthPrincipal actor, @PathVariable long id,
            @Valid @RequestBody(required = false) PackOrderRequest request) {
        return service.pack(actor.id(), id, request);
    }

    /** Nộp (upsert) báo cáo ngày của người dùng hiện tại. */
    @PostMapping("/reports/daily")
    public DailyReportView submitDailyReport(@AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody DailyReportRequest request) {
        return reports.submit(actor.id(), request.content());
    }

    /** 10 báo cáo gần nhất của nhân viên kho. */
    @GetMapping("/reports/daily/recent")
    public List<DailyReportView> recentDailyReports(@AuthenticationPrincipal AuthPrincipal actor) {
        return reports.recent(actor.id(), "WAREHOUSE");
    }
}
