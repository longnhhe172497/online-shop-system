package com.osmshop.order;

import static com.osmshop.order.CheckoutRules.error;

import com.osmshop.auth.AuthApiException;
import com.osmshop.order.CheckoutRules.Line;
import com.osmshop.order.OrderDtos.OrderDetail;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Simulated QR payments. No gateway is called and no real money moves: the customer chooses the
 * outcome. A failed or expired attempt gives its stock and voucher back; a retry re-validates
 * everything and reserves again. When the last allowed attempt fails or expires, the order is cancelled.
 */
@Service
public class PaymentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentService.class);
    private final JdbcTemplate jdbc;
    private final OrderStore store;
    private final OrderService orders;
    private final VoucherService vouchers;
    private final TransactionTemplate transactions;

    public PaymentService(JdbcTemplate jdbc, OrderStore store, OrderService orders, VoucherService vouchers,
                          TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.store = store;
        this.orders = orders;
        this.vouchers = vouchers;
        this.transactions = transactions;
    }

    private record Attempt(long id, long orderId, int number, String method, String status, boolean expired) {}

    @Transactional(noRollbackFor = AuthApiException.class)
    public OrderDetail simulate(long customerId, long attemptId, String outcome) {
        Long orderId = jdbc.query("""
                SELECT pa.order_id FROM payment_attempts pa JOIN orders o ON o.id = pa.order_id
                WHERE pa.id = ? AND o.customer_id = ?
                """, (rs, row) -> rs.getLong(1), attemptId, customerId).stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PAYMENT_ATTEMPT_NOT_FOUND", "Payment attempt not found"));
        store.lockOrder(customerId, orderId);
        Attempt attempt = attempt(attemptId);
        if (!"SIMULATED_QR".equals(attempt.method()) || !"PENDING".equals(attempt.status()))
            throw error(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_NOT_PENDING", "Payment attempt is not waiting for payment");
        if (attempt.expired()) {
            // Persisted thanks to noRollbackFor: the expiry stands even though the request fails.
            expire(attempt);
            throw error(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_EXPIRED", "Payment attempt has expired");
        }
        if ("SUCCESS".equals(outcome)) {
            jdbc.update("UPDATE payment_attempts SET status = 'PAID', paid_at = now() WHERE id = ?", attemptId);
            jdbc.update("""
                    UPDATE orders SET status = 'CONFIRMED', confirmed_at = now(), updated_at = now()
                    WHERE id = ? AND status = 'AWAITING_PAYMENT'
                    """, orderId);
            jdbc.update("""
                    UPDATE voucher_usages SET status = 'CONSUMED', consumed_at = now()
                    WHERE order_id = ? AND status = 'RESERVED'
                    """, orderId);
            store.audit(customerId, "PAYMENT_SUCCEEDED", orderId, "attempt " + attempt.number());
            store.publish(orderId, OrderEvent.Kind.PAID);
        } else {
            jdbc.update("UPDATE payment_attempts SET status = 'FAILED' WHERE id = ?", attemptId);
            store.audit(customerId, "PAYMENT_FAILED", orderId, "attempt " + attempt.number());
            endAttempt(attempt);
        }
        return orders.getOrder(customerId, orderId);
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public OrderDetail retry(long customerId, long orderId) {
        OrderStore.LockedOrder order = store.lockOrder(customerId, orderId);
        if (!"SIMULATED_QR".equals(order.paymentMethod()) || !"AWAITING_PAYMENT".equals(order.status()))
            throw error(HttpStatus.CONFLICT, "PAYMENT_RETRY_NOT_ALLOWED", "Only unpaid QR orders can get a new QR code");
        Attempt latest = jdbc.query("""
                SELECT id FROM payment_attempts WHERE order_id = ? ORDER BY attempt_number DESC LIMIT 1 FOR UPDATE
                """, (rs, row) -> rs.getLong(1), orderId).stream().findFirst().map(this::attempt).orElse(null);
        int maxAttempts = store.intSetting("max_qr_attempts", 3);
        if (latest != null && "PENDING".equals(latest.status())) {
            if (!latest.expired()) throw error(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_ALREADY_PENDING",
                    "The current QR code is still valid");
            if (expire(latest)) throw exhausted();
        }
        int next = latest == null ? 1 : latest.number() + 1;
        if (next > maxAttempts) throw exhausted();

        // Fresh validation: the customer pays today's reality, not a stale snapshot.
        record Item(long itemId, Line line, BigDecimal snapshotPrice) {}
        List<Item> items = jdbc.query("""
                SELECT i.id,p.id,p.sku,p.name,p.price,p.status = 'ACTIVE' AND c.status = 'ACTIVE',
                       p.stock_on_hand - p.stock_reserved,i.quantity,i.unit_price
                FROM order_items i
                JOIN products p ON p.id = i.product_id
                JOIN categories c ON c.id = p.category_id
                WHERE i.order_id = ?
                ORDER BY p.id
                FOR UPDATE OF p
                """, (rs, row) -> new Item(rs.getLong(1), new Line(rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBoolean(6), rs.getInt(7), rs.getInt(8)), rs.getBigDecimal(9)),
                orderId);
        OrderService.requireSellable(items.stream().map(Item::line).toList());
        for (Item item : items) {
            if (item.line().price().compareTo(item.snapshotPrice()) != 0) throw error(HttpStatus.CONFLICT,
                    "PRICE_CHANGED", "The price of " + item.line().sku() + " changed; cancel and order again");
        }
        if (order.voucherId() != null) {
            vouchers.usableById(order.voucherId(), order.subtotal());
            jdbc.update("""
                    UPDATE voucher_usages SET status = 'RESERVED', reserved_at = now(), released_at = NULL
                    WHERE order_id = ?
                    """, orderId);
        }
        long attemptId = orders.createQrAttempt(orderId, next, order.totalAmount());
        for (Item item : items) orders.reserve(item.itemId(), item.line().productId(), item.line().quantity(), attemptId);
        store.audit(customerId, "PAYMENT_RETRIED", orderId, "attempt " + next);
        return orders.getOrder(customerId, orderId);
    }

    /** Expires overdue PENDING QR attempts, one transaction per attempt. Returns how many were expired. */
    public int expireDue() {
        List<Long> due = jdbc.queryForList("""
                SELECT id FROM payment_attempts
                WHERE status = 'PENDING' AND method = 'SIMULATED_QR' AND expires_at <= now()
                ORDER BY expires_at LIMIT 200
                """, Long.class);
        int expired = 0;
        for (long attemptId : due) {
            try {
                Boolean done = transactions.execute(status -> {
                    Long orderId = jdbc.queryForObject("SELECT order_id FROM payment_attempts WHERE id = ?",
                            Long.class, attemptId);
                    jdbc.queryForList("SELECT id FROM orders WHERE id = ? FOR UPDATE", Long.class, orderId);
                    Attempt attempt = attempt(attemptId);
                    if (!"PENDING".equals(attempt.status()) || !attempt.expired()) return false;
                    expire(attempt);
                    return true;
                });
                if (Boolean.TRUE.equals(done)) expired++;
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not expire payment attempt {}", attemptId, exception);
            }
        }
        return expired;
    }

    /** Marks the attempt EXPIRED and ends it. Returns true when that also cancelled the order. */
    private boolean expire(Attempt attempt) {
        jdbc.update("UPDATE payment_attempts SET status = 'EXPIRED' WHERE id = ?", attempt.id());
        store.audit(null, "PAYMENT_EXPIRED", attempt.orderId(), "attempt " + attempt.number());
        return endAttempt(attempt);
    }

    /** Gives back the attempt's stock and the voucher; cancels the order after the last allowed attempt. */
    private boolean endAttempt(Attempt attempt) {
        store.releaseReservations(attempt.orderId(), attempt.id());
        store.releaseVoucher(attempt.orderId());
        if (attempt.number() < store.intSetting("max_qr_attempts", 3)) return false;
        store.cancel(attempt.orderId(), null, "Hết số lần thanh toán QR");
        return true;
    }

    private Attempt attempt(long attemptId) {
        return jdbc.queryForObject("""
                SELECT id,order_id,attempt_number,method,status,coalesce(expires_at <= now(), false)
                FROM payment_attempts WHERE id = ? FOR UPDATE
                """, (rs, row) -> new Attempt(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6)), attemptId);
    }

    private static AuthApiException exhausted() {
        return error(HttpStatus.CONFLICT, "PAYMENT_ATTEMPTS_EXHAUSTED", "No QR attempts left; the order was cancelled");
    }
}
