package com.osmshop.logistics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class LogisticsDtos {
    private LogisticsDtos() {}

    // ---- Kho ----
    public record ProductStockView(long id, String sku, String name, BigDecimal price, int onHandQty,
                                   int reservedQty, int availableQty, int reorderThreshold,
                                   boolean lowStock, String status) {}

    public record RestockRequest(@Min(1) @Max(100000) int quantity,
                                 @Size(max = 100) String referenceId,
                                 @Size(max = 1000) String notes) {}

    public record TransactionView(long id, long productId, String type, int quantityDelta, int balanceAfter,
                                  String referenceId, String notes, Long staffId, OffsetDateTime createdAt) {}

    public record RestockResponse(ProductStockView product, TransactionView transaction) {}

    public record OrderItemView(long productId, String productName, String sku, int quantity) {}

    public record PackingOrderView(long id, String orderCode, String recipientName, String recipientPhone,
                                   String deliveryAddress, BigDecimal totalAmount, String paymentMethod,
                                   String paymentStatus, String status, OffsetDateTime createdAt,
                                   List<OrderItemView> items) {}

    public record PackOrderRequest(@Size(max = 100) String carrierName, @Size(max = 1000) String notes) {}

    // ---- Giao hàng ----
    public record UpdateShipmentStatusRequest(@NotNull ShipmentStatus status,
                                              @Size(max = 1000) String note,
                                              @Size(max = 255) String failedReason,
                                              Boolean codCollected) {}

    public record ShipmentListItem(long id, long orderId, String orderCode, String trackingNumber,
                                   String carrierName, ShipmentStatus status, Long deliveryStaffId,
                                   Long warehouseStaffId, String recipientName, String recipientPhone,
                                   String deliveryAddress, String paymentMethod, BigDecimal orderTotal,
                                   BigDecimal codAmount, boolean codCollected, String deliveryNotes,
                                   String failedReason, OffsetDateTime createdAt, OffsetDateTime dispatchedAt,
                                   OffsetDateTime deliveredAt) {}

    public record HistoryView(long id, ShipmentStatus previousStatus, ShipmentStatus newStatus, String note,
                              Long updatedBy, OffsetDateTime createdAt) {}

    public record ShipmentDetailResponse(ShipmentListItem shipment, List<OrderItemView> items,
                                         List<HistoryView> history) {}

    // ---- Báo cáo ngày (bảng staff_daily_reports) ----
    public record DailyReportRequest(@NotBlank @Size(max = 4000) String content) {}

    /** timeAgo: "Today", "Yesterday", hoặc ngày dd/MM/yyyy nếu cũ hơn. */
    public record DailyReportView(long id, String staffName, LocalDate reportDate, String timeAgo,
                                  String content, OffsetDateTime updatedAt, boolean mine) {}
}
