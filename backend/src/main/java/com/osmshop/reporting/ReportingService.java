package com.osmshop.reporting;

import com.osmshop.auth.AuthApiException;
import com.osmshop.support.AfterSalesDtos.DailyMetric;
import com.osmshop.support.AfterSalesDtos.ReportSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingService {
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

    private static void validate(LocalDate from,LocalDate to){
        if(from==null||to==null||to.isBefore(from)||to.isAfter(LocalDate.now().plusDays(1))||from.isBefore(to.minusDays(365)))
            throw new AuthApiException(HttpStatus.BAD_REQUEST,"INVALID_REPORT_RANGE","Khoảng thời gian báo cáo không hợp lệ (tối đa 366 ngày).");
    }
}
