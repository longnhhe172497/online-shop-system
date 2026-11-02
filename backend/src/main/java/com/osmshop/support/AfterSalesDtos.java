package com.osmshop.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AfterSalesDtos {
    private AfterSalesDtos() {}
    public record ReturnItemRequest(@NotNull @Positive Long orderItemId, @Min(1) @Max(100000) int quantity) {}
    public record CreateReturnRequest(@NotBlank @Size(max = 25) String requestType,
            @NotBlank @Size(max = 2000) String reason, @Size(max = 500) String evidenceUrl,
            @NotEmpty List<@Valid ReturnItemRequest> items) {}
    public record ReturnItemView(long id, long orderItemId, String productName, String sku,
            int requestedQuantity, BigDecimal refundAmount, Integer receivedQuantity,
            Integer restockQuantity, String inspectionResult, String conditionNote) {}
    public record ReturnView(long id, long orderId, String orderCode, long customerId,
            String customerName, String requestType, String status, String reason,
            String evidenceUrl, String decisionNote, OffsetDateTime createdAt, List<ReturnItemView> items) {}
    public record ReturnDecisionRequest(@NotNull Boolean approved, @Size(max = 1000) String note) {}
    public record ItemInspection(@NotNull @Positive Long returnRequestItemId,
            @Min(0) @Max(100000) int receivedQuantity, @NotBlank @Size(max = 1000) String conditionNote,
            @Min(0) @Max(100000) int restockQuantity) {}
    public record InspectionRequest(@NotEmpty List<@Valid ItemInspection> items) {}
    public record RefundView(long id, long orderId, Long returnRequestId, String orderCode,
            String reason, String method, String status, BigDecimal amount,
            OffsetDateTime createdAt, OffsetDateTime completedAt) {}
    public record CreateTicketRequest(@NotBlank @Size(max = 200) String subject,
            @Positive Long orderId, @NotBlank @Size(max = 5000) String message) {}
    public record TicketMessageRequest(@NotBlank @Size(max = 5000) String message) {}
    public record TicketStatusRequest(@NotBlank @Pattern(regexp = "IN_PROGRESS|RESOLVED|CLOSED") String status) {}
    public record TicketMessageView(long id, long senderId, String senderName, String message, OffsetDateTime createdAt) {}
    public record TicketView(long id, long customerId, String customerName, Long orderId,
            String orderCode, String subject, String status, Long assignedStaffId,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, List<TicketMessageView> messages) {}
    public record DailyMetric(String date, long orderCount, long paidOrderCount,
            BigDecimal grossRevenue, BigDecimal refundedAmount, long refundCount) {}
    public record ReportSummary(String from, String to, long orderCount, long paidOrderCount,
            BigDecimal grossRevenue, BigDecimal refundedAmount, long refundCount, List<DailyMetric> daily) {}
}
