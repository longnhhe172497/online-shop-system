package com.osmshop.order;

import static com.osmshop.order.CheckoutRules.error;

import com.osmshop.order.FulfillmentDtos.StaffOrderView;
import com.osmshop.order.OrderDtos.OrderItemView;
import com.osmshop.order.OrderDtos.ShipmentTracking;
import com.osmshop.order.OrderDtos.TrackingAttempt;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read models shared by the customer tracking view and the Support/Manager order screens. */
@Component
class FulfillmentQueries {
    /** Order states in which a shipper can still be (re)assigned before the parcel leaves. */
    static final Set<String> ASSIGNABLE = Set.of("READY_FOR_DELIVERY", "ASSIGNED", "DELIVERY_FAILED");
    private final JdbcTemplate jdbc;

    FulfillmentQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The shipment created when the warehouse packed the order, with its delivery attempts. */
    Optional<ShipmentTracking> tracking(long orderId) {
        record Row(long id, String code, String status, String staff, OffsetDateTime packed, OffsetDateTime assigned,
                   BigDecimal cod, OffsetDateTime collected) {}
        return jdbc.query("""
                SELECT s.id,o.order_code,s.status,u.full_name,s.created_at,s.assigned_at,s.cod_amount,s.cod_collected_at
                FROM shipments s JOIN orders o ON o.id = s.order_id LEFT JOIN users u ON u.id = s.assigned_staff_id
                WHERE s.order_id = ?
                """, (rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, OffsetDateTime.class), rs.getObject(6, OffsetDateTime.class),
                        rs.getBigDecimal(7), rs.getObject(8, OffsetDateTime.class)), orderId)
                .stream().findFirst()
                .map(row -> new ShipmentTracking(row.id(), "TRK-" + row.code(), row.status(), row.staff(), row.packed(),
                        row.assigned(), row.cod(), row.collected(), attempts(row.id())));
    }

    private List<TrackingAttempt> attempts(long shipmentId) {
        return jdbc.query("""
                SELECT attempt_number,status,failure_reason,started_at,finished_at
                FROM delivery_attempts WHERE shipment_id = ? ORDER BY attempt_number, id
                """, (rs, n) -> new TrackingAttempt(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class), rs.getObject(5, OffsetDateTime.class)), shipmentId);
    }

    List<OrderItemView> items(long orderId) {
        return jdbc.query("""
                SELECT id,product_id,sku_snapshot,name_snapshot,unit_price,quantity,discount_amount,line_total
                FROM order_items WHERE order_id = ? ORDER BY id
                """, (rs, n) -> new OrderItemView(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getInt(6), rs.getBigDecimal(7), rs.getBigDecimal(8)), orderId);
    }

    /** Full order as staff see it, including the customer and the internal note. */
    StaffOrderView staffView(long orderId) {
        record Header(long id, String code, String status, String method, String payment, String customer,
                      String email, String recipient, String phone, String address, BigDecimal subtotal,
                      BigDecimal discount, BigDecimal shipping, BigDecimal total, String voucher, String note,
                      OffsetDateTime created, OffsetDateTime confirmed, String confirmedBy, OffsetDateTime cancelled) {}
        Header h = jdbc.query("""
                SELECT o.id,o.order_code,o.status,o.payment_method,
                       (SELECT pa.status FROM payment_attempts pa WHERE pa.order_id = o.id
                        ORDER BY pa.attempt_number DESC LIMIT 1),
                       c.full_name,c.email,o.recipient_name,o.recipient_phone,o.shipping_address,
                       o.subtotal,o.discount_amount,o.shipping_fee,o.total_amount,v.code,o.internal_note,
                       o.created_at,o.confirmed_at,cb.full_name,o.cancelled_at
                FROM orders o JOIN users c ON c.id = o.customer_id
                LEFT JOIN vouchers v ON v.id = o.voucher_id LEFT JOIN users cb ON cb.id = o.confirmed_by
                WHERE o.id = ?
                """, (rs, n) -> new Header(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getBigDecimal(11), rs.getBigDecimal(12), rs.getBigDecimal(13),
                        rs.getBigDecimal(14), rs.getString(15), rs.getString(16),
                        rs.getObject(17, OffsetDateTime.class), rs.getObject(18, OffsetDateTime.class),
                        rs.getString(19), rs.getObject(20, OffsetDateTime.class)), orderId)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
        Optional<ShipmentTracking> shipment = tracking(orderId);
        boolean assignable = ASSIGNABLE.contains(h.status()) && shipment.isPresent()
                && Set.of("READY", "ASSIGNED", "DELIVERY_FAILED").contains(shipment.get().status());
        return new StaffOrderView(h.id(), h.code(), h.status(), h.method(), h.payment(), h.customer(), h.email(),
                h.recipient(), h.phone(), h.address(), h.subtotal(), h.discount(), h.shipping(), h.total(), h.voucher(),
                h.note(), h.created(), h.confirmed(), h.confirmedBy(), h.cancelled(), items(orderId),
                shipment.orElse(null), assignable);
    }

    /** Locks an order by id for a staff action; returns {status, payment_method}. */
    String[] lockOrder(long orderId) {
        return jdbc.query("SELECT status,payment_method FROM orders WHERE id = ? FOR UPDATE",
                        (rs, n) -> new String[] {rs.getString(1), rs.getString(2)}, orderId)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
    }
}
