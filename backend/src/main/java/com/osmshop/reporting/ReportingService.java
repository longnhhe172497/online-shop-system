package com.osmshop.reporting;

import com.osmshop.auth.AuthApiException;
import com.osmshop.reporting.ReportingDtos.*;
import com.osmshop.support.AfterSalesDtos.DailyMetric;
import com.osmshop.support.AfterSalesDtos.ReportSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingService {
    static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    public ReportingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public ReportSummary summary(LocalDate from, LocalDate to) {
        validate(from,to);
        List<DailyMetric> daily=jdbc.query("""
                WITH days AS (SELECT generate_series(?::date,?::date,interval '1 day')::date AS day),
                orders_daily AS (
                    SELECT (o.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,count(*) AS orders
                    FROM orders o WHERE (o.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
                    GROUP BY 1
                ), payment_transactions AS (
                    SELECT (p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,p.order_id,p.amount
                    FROM (
                        SELECT order_id,max(paid_at) AS paid_at,max(amount) AS amount
                        FROM payment_attempts WHERE method='SIMULATED_QR' AND status='PAID' AND paid_at IS NOT NULL
                        GROUP BY order_id
                    ) p
                    WHERE (p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
                    UNION ALL
                    SELECT (s.cod_collected_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,s.order_id,s.cod_amount AS amount
                    FROM shipments s WHERE s.cod_collected_at IS NOT NULL
                      AND (s.cod_collected_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
                ), paid_daily AS (
                    SELECT day,count(DISTINCT order_id) AS paid,sum(amount) AS gross
                    FROM payment_transactions GROUP BY day
                ), refunds_daily AS (
                    SELECT (r.completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,
                           sum(r.amount) AS refunded,count(*) AS refund_count
                    FROM refunds r WHERE r.status='COMPLETED' AND r.completed_at IS NOT NULL
                      AND (r.completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ? GROUP BY 1
                )
                SELECT d.day,COALESCE(o.orders,0),COALESCE(p.paid,0),COALESCE(p.gross,0),
                       COALESCE(r.refunded,0),COALESCE(r.refund_count,0)
                FROM days d LEFT JOIN orders_daily o ON o.day=d.day
                LEFT JOIN paid_daily p ON p.day=d.day LEFT JOIN refunds_daily r ON r.day=d.day
                ORDER BY d.day
                """,(rs,row)->new DailyMetric(rs.getDate(1).toLocalDate().toString(),rs.getLong(2),rs.getLong(3),rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getLong(6)),
                from,to,from,to,from,to,from,to,from,to);
        long orders=0,paid=0,refundCount=0; BigDecimal gross=BigDecimal.ZERO,refunded=BigDecimal.ZERO;
        for(DailyMetric d:daily){orders+=d.orderCount();paid+=d.paidOrderCount();refundCount+=d.refundCount();gross=gross.add(d.grossRevenue());refunded=refunded.add(d.refundedAmount());}
        return new ReportSummary(from.toString(),to.toString(),orders,paid,gross,refunded,refundCount,daily);
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(LocalDate from, LocalDate to) {
        ReportSummary summary = summary(from, to);
        List<TopProduct> top = jdbc.query("""
                SELECT oi.product_id,max(oi.sku_snapshot),max(oi.name_snapshot),sum(oi.quantity),sum(oi.line_total)
                FROM order_items oi JOIN orders o ON o.id=oi.order_id
                WHERE o.status NOT IN ('AWAITING_PAYMENT','CANCELLED')
                  AND (o.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
                GROUP BY oi.product_id ORDER BY sum(oi.quantity) DESC,sum(oi.line_total) DESC LIMIT 5
                """, (rs, row) -> new TopProduct(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getBigDecimal(5)),
                from, to);
        List<LowStockItem> low = jdbc.query("""
                SELECT id,sku,name,stock_on_hand-stock_reserved,reorder_threshold FROM products
                WHERE status='ACTIVE' AND stock_on_hand-stock_reserved<=reorder_threshold
                ORDER BY stock_on_hand-stock_reserved-reorder_threshold,name LIMIT 10
                """, (rs, row) -> new LowStockItem(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5)));
        PendingActions pending = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM return_requests WHERE status IN ('REQUESTED','INSPECTED')),
                       (SELECT count(*) FROM refunds WHERE status='PENDING'),
                       (SELECT count(*) FROM support_tickets WHERE status='OPEN' AND assigned_staff_id IS NULL),
                       (SELECT count(*) FROM orders WHERE status='DELIVERY_FAILED'),
                       (SELECT count(*) FROM orders o WHERE o.status='RETURNED_TO_WAREHOUSE' AND o.payment_method='SIMULATED_QR'
                          AND EXISTS (SELECT 1 FROM payment_attempts p WHERE p.order_id=o.id AND p.method='SIMULATED_QR' AND p.status='PAID')
                          AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.order_id=o.id AND r.status<>'FAILED'))
                """, (rs, row) -> new PendingActions(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)));
        return new DashboardView(summary, top, low, pending);
    }

    @Transactional(readOnly = true)
    public List<StaffReportRow> dailyReports(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(REPORT_ZONE) : date;
        return jdbc.query("""
                SELECT u.full_name,u.role,r.report_date,r.content,r.updated_at
                FROM staff_daily_reports r JOIN users u ON u.id=r.staff_id
                WHERE r.report_date=? AND u.role IN ('WAREHOUSE','DELIVERY')
                ORDER BY r.updated_at DESC,r.id DESC
                """, (rs, row) -> new StaffReportRow(rs.getString(1), rs.getString(2),
                rs.getDate(3).toLocalDate().toString(), rs.getString(4), rs.getObject(5, OffsetDateTime.class)), day);
    }

    private static void validate(LocalDate from,LocalDate to){
        if(from==null||to==null||to.isBefore(from)||to.isAfter(LocalDate.now(REPORT_ZONE).plusDays(1))||from.isBefore(to.minusDays(365)))
            throw new AuthApiException(HttpStatus.BAD_REQUEST,"INVALID_REPORT_RANGE","Khoảng thời gian báo cáo không hợp lệ (tối đa 366 ngày).");
    }
}
