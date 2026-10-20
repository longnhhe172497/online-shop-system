package com.osmshop.order;

import static com.osmshop.order.CheckoutRules.error;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Shared order bookkeeping. Every path that gives stock back (payment failure, QR expiry,
 * cancellation) goes through {@link #releaseReservations} so stock_reserved stays in step.
 * Callers must already hold the order row lock from {@link #lockOrder}.
 */
@Component
class OrderStore {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    OrderStore(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    record LockedOrder(long id, long customerId, String status, String paymentMethod, Long voucherId,
                       BigDecimal subtotal, BigDecimal totalAmount) {}

    /** Locks an order owned by the customer; another customer's order is reported as absent. */
    LockedOrder lockOrder(long customerId, long orderId) {
        return jdbc.query("""
                SELECT id,customer_id,status,payment_method,voucher_id,subtotal,total_amount
                FROM orders WHERE id = ? AND customer_id = ? FOR UPDATE
                """, (rs, row) -> new LockedOrder(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, Long.class), rs.getBigDecimal(6), rs.getBigDecimal(7)), orderId, customerId)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
    }

    /** Releases ACTIVE reservations of the whole order, or only those of one payment attempt. */
    void releaseReservations(long orderId, Long attemptId) {
        jdbc.update("""
                WITH released AS (
                    UPDATE stock_reservations r SET status = 'RELEASED', finished_at = now()
                    FROM order_items i
                    WHERE i.id = r.order_item_id AND i.order_id = ? AND r.status = 'ACTIVE'
                      AND (?::bigint IS NULL OR r.payment_attempt_id = ?::bigint)
                    RETURNING i.product_id, r.quantity)
                UPDATE products p SET stock_reserved = p.stock_reserved - x.quantity, updated_at = now()
                FROM (SELECT product_id, sum(quantity) AS quantity FROM released GROUP BY product_id) x
                WHERE p.id = x.product_id
                """, orderId, attemptId, attemptId);
    }

    void releaseVoucher(long orderId) {
        jdbc.update("""
                UPDATE voucher_usages SET status = 'RELEASED', released_at = now()
                WHERE order_id = ? AND status IN ('RESERVED','CONSUMED')
                """, orderId);
    }

    /** Ends an order before packing: frees stock and voucher and stops unfinished payments. */
    void cancel(long orderId, Long actorId, String note) {
        releaseReservations(orderId, null);
        releaseVoucher(orderId);
        jdbc.update("""
                UPDATE payment_attempts SET status = 'CANCELLED'
                WHERE order_id = ? AND status IN ('PENDING','UNPAID')
                """, orderId);
        jdbc.update("""
                UPDATE orders SET status = 'CANCELLED', cancelled_at = now(), updated_at = now(), internal_note = ?
                WHERE id = ?
                """, note, orderId);
        audit(actorId, "ORDER_CANCELLED", orderId, note);
        events.publishEvent(new OrderEvent(orderId, OrderEvent.Kind.CANCELLED));
    }

    int intSetting(String key, int fallback) {
        List<String> values = jdbc.queryForList(
                "SELECT value_text FROM system_settings WHERE setting_key = ?", String.class, key);
        return values.isEmpty() ? fallback : Integer.parseInt(values.getFirst());
    }

    void audit(Long actorId, String action, long orderId, String detail) {
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                VALUES (?,?,'ORDER',?,jsonb_build_object('detail',?::text))
                """, actorId, action, Long.toString(orderId), detail);
    }

    void publish(long orderId, OrderEvent.Kind kind) {
        events.publishEvent(new OrderEvent(orderId, kind));
    }
}
