package com.osmshop.reporting;

import com.osmshop.support.AfterSalesDtos.ReportSummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class ReportingDtos {
    private ReportingDtos() {}
    public record TopProduct(long productId, String sku, String name, long unitsSold, BigDecimal revenue) {}
    public record LowStockItem(long productId, String sku, String name, int available, int reorderThreshold) {}
    public record PendingActions(long returnRequestsToDecide, long pendingRefunds, long unassignedOpenTickets,
            long deliveryFailedOrders, long paidQrReturnedWithoutRefund) {}
    public record DashboardView(ReportSummary summary, List<TopProduct> topProducts,
            List<LowStockItem> lowStock, PendingActions pendingActions) {}
    public record StaffReportRow(String staffName, String role, String reportDate, String content,
            OffsetDateTime updatedAt) {}
}
