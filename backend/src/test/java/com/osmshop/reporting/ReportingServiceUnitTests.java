package com.osmshop.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.osmshop.auth.AuthApiException;
import com.osmshop.reporting.ReportingDtos.DashboardView;
import com.osmshop.reporting.ReportingDtos.LowStockItem;
import com.osmshop.reporting.ReportingDtos.PendingActions;
import com.osmshop.reporting.ReportingDtos.StaffReportRow;
import com.osmshop.reporting.ReportingDtos.TopProduct;
import com.osmshop.support.AfterSalesDtos.DailyMetric;
import com.osmshop.support.AfterSalesDtos.ReportSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Pure service tests for the Manager dashboard, staff daily reports and transaction summary
 * (UC-MNG-006, UC-MNG-007, UC-MNG-010): no Spring context, HTTP layer or database.
 */
class ReportingServiceUnitTests {
    private static final LocalDate FROM = LocalDate.parse("2026-11-01");
    private static final LocalDate TO = LocalDate.parse("2026-11-02");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReportingService service = new ReportingService(jdbc);

    // ---------- summary / export range (UC-MNG-010) ----------

    @Test
    void summary_whenRangeReversed_rejectsBeforeQuery() {
        AuthApiException error = assertThrows(AuthApiException.class, () -> service.summary(TO, FROM));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertEquals("INVALID_REPORT_RANGE", error.code());
        verifyNoInteractions(jdbc);
    }

    @Test
    void summary_whenRangeLongerThan366Days_rejectsBeforeQuery() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.summary(LocalDate.parse("2025-01-01"), LocalDate.parse("2026-11-01")));

        assertEquals("INVALID_REPORT_RANGE", error.code());
        verifyNoInteractions(jdbc);
    }

    @Test
    void summary_whenEndDateFarInFuture_rejectsBeforeQuery() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        AuthApiException error = assertThrows(AuthApiException.class, () -> service.summary(today, today.plusDays(5)));

        assertEquals("INVALID_REPORT_RANGE", error.code());
        verifyNoInteractions(jdbc);
    }

    @Test
    void summary_whenValid_addsDailyMetricsIntoTotals() {
        stubDaily();

        ReportSummary result = service.summary(FROM, TO);

        assertEquals(5, result.orderCount());
        assertEquals(3, result.paidOrderCount());
        assertEquals(0, new BigDecimal("700000").compareTo(result.grossRevenue()));
        assertEquals(0, new BigDecimal("50000").compareTo(result.refundedAmount()));
        assertEquals(1, result.refundCount());
        assertEquals(2, result.daily().size());
    }

    // ---------- dashboard (UC-MNG-006) ----------

    @Test
    void dashboard_whenRangeInvalid_rejectsBeforeQuery() {
        AuthApiException error = assertThrows(AuthApiException.class, () -> service.dashboard(TO, FROM));

        assertEquals("INVALID_REPORT_RANGE", error.code());
        verifyNoInteractions(jdbc);
    }

    @Test
    void dashboard_whenValid_combinesSummaryTopProductsLowStockAndPendingActions() {
        stubDaily();
        List<TopProduct> top = List.of(new TopProduct(1L, "AO-01", "Shirt", 4L, new BigDecimal("520000")));
        List<LowStockItem> low = List.of(new LowStockItem(2L, "VAY-02", "Dress", 0, 3));
        PendingActions pending = new PendingActions(2L, 1L, 3L, 0L, 1L);
        when(jdbc.query(contains("FROM order_items oi JOIN orders o"), ArgumentMatchers.<RowMapper<TopProduct>>any(), any(), any()))
                .thenReturn(top);
        when(jdbc.query(contains("FROM products"), ArgumentMatchers.<RowMapper<LowStockItem>>any())).thenReturn(low);
        when(jdbc.queryForObject(contains("FROM return_requests"), ArgumentMatchers.<RowMapper<PendingActions>>any()))
                .thenReturn(pending);

        DashboardView result = service.dashboard(FROM, TO);

        assertEquals(5, result.summary().orderCount());
        assertEquals(top, result.topProducts());
        assertEquals(low, result.lowStock());
        assertEquals(pending, result.pendingActions());
        verify(jdbc).query(contains("FROM order_items oi JOIN orders o"), ArgumentMatchers.<RowMapper<TopProduct>>any(), eq(FROM), eq(TO));
    }

    @Test
    void dashboard_lowStockQuery_onlyCountsActiveProductsAtOrBelowThreshold() {
        stubDaily();

        service.dashboard(FROM, TO);

        verify(jdbc).query(ArgumentMatchers.<String>argThat(sql -> sql.contains("status='ACTIVE'")
                && sql.contains("stock_on_hand-stock_reserved<=reorder_threshold")),
                ArgumentMatchers.<RowMapper<LowStockItem>>any());
    }

    // ---------- staff daily reports (UC-MNG-007) ----------

    @Test
    void dailyReports_whenDateMissing_usesTodayInVietnamTime() {
        service.dailyReports(null);

        verify(jdbc).query(anyString(), ArgumentMatchers.<RowMapper<StaffReportRow>>any(),
                eq(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
    }

    @Test
    void dailyReports_whenDateGiven_queriesThatDateForWarehouseAndDeliveryOnly() {
        StaffReportRow row = new StaffReportRow("Hung", "WAREHOUSE", "2026-11-05", "Packed 12 orders", OffsetDateTime.now());
        when(jdbc.query(contains("FROM staff_daily_reports"), ArgumentMatchers.<RowMapper<StaffReportRow>>any(), any()))
                .thenReturn(List.of(row));

        List<StaffReportRow> result = service.dailyReports(LocalDate.parse("2026-11-05"));

        assertEquals(List.of(row), result);
        verify(jdbc).query(ArgumentMatchers.<String>argThat(sql -> sql.contains("u.role IN ('WAREHOUSE','DELIVERY')")),
                ArgumentMatchers.<RowMapper<StaffReportRow>>any(), eq(LocalDate.parse("2026-11-05")));
    }

    @Test
    void dailyReports_whenNoReports_returnsEmptyList() {
        assertTrue(service.dailyReports(LocalDate.parse("2026-11-05")).isEmpty());
    }

    // ---------- helpers ----------

    private void stubDaily() {
        List<DailyMetric> daily = List.of(
                new DailyMetric("2026-11-01", 2, 1, new BigDecimal("300000"), BigDecimal.ZERO, 0),
                new DailyMetric("2026-11-02", 3, 2, new BigDecimal("400000"), new BigDecimal("50000"), 1));
        when(jdbc.query(contains("generate_series"), ArgumentMatchers.<RowMapper<DailyMetric>>any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(daily);
    }
}
