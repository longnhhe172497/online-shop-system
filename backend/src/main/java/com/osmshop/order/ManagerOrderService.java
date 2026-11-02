package com.osmshop.order;

import static com.osmshop.order.CheckoutRules.error;

import com.osmshop.api.PageResponse;
import com.osmshop.order.FulfillmentDtos.DeliveryStaffOption;
import com.osmshop.order.FulfillmentDtos.StaffOrderSummary;
import com.osmshop.order.FulfillmentDtos.StaffOrderView;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational Manager: view every order (UC-MNG-008) and assign a packed order to a Delivery Staff
 * member (UC-MNG-009). Reassigning is allowed until the parcel leaves, and after a failed attempt.
 */
@Service
public class ManagerOrderService {
    static final Set<String> STATUSES = Set.of("AWAITING_PAYMENT", "PENDING_CONFIRMATION", "CONFIRMED",
            "READY_FOR_DELIVERY", "ASSIGNED", "DELIVERING", "DELIVERED", "DELIVERY_FAILED", "RETURNED_TO_WAREHOUSE",
            "CANCELLED");
    private final JdbcTemplate jdbc;
    private final OrderStore store;
    private final FulfillmentQueries queries;

    public ManagerOrderService(JdbcTemplate jdbc, OrderStore store, FulfillmentQueries queries) {
        this.jdbc = jdbc;
        this.store = store;
        this.queries = queries;
    }

    /** Newest first; search matches order code, customer name/email or recipient phone. */
    public PageResponse<StaffOrderSummary> list(String status, String search, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            String value = status.trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(value)) throw error(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown order status");
            where.append(" AND o.status = ?");
            args.add(value);
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (upper(o.order_code) LIKE ? OR lower(c.full_name) LIKE ? OR lower(c.email) LIKE ?"
                    + " OR o.recipient_phone LIKE ?)");
            String term = search.trim();
            args.add("%" + term.toUpperCase(Locale.ROOT) + "%");
            args.add("%" + term.toLowerCase(Locale.ROOT) + "%");
            args.add("%" + term.toLowerCase(Locale.ROOT) + "%");
            args.add("%" + term + "%");
        }
        String from = " FROM orders o JOIN users c ON c.id = o.customer_id"
                + " LEFT JOIN shipments s ON s.order_id = o.id LEFT JOIN users d ON d.id = s.assigned_staff_id";
        Long total = jdbc.queryForObject("SELECT count(*)" + from + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<StaffOrderSummary> items = jdbc.query("""
                SELECT o.id,o.order_code,o.status,o.payment_method,
                       (SELECT pa.status FROM payment_attempts pa WHERE pa.order_id = o.id
                        ORDER BY pa.attempt_number DESC LIMIT 1),
                       c.full_name,o.recipient_phone,o.total_amount,
                       (SELECT coalesce(sum(i.quantity),0) FROM order_items i WHERE i.order_id = o.id),
                       o.created_at,s.status,d.full_name
                """ + from + where + " ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new StaffOrderSummary(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getInt(9),
                        rs.getObject(10, OffsetDateTime.class), rs.getString(11), rs.getString(12)), args.toArray());
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public StaffOrderView get(long orderId) {
        return queries.staffView(orderId);
    }

    /** Active, verified Delivery Staff with how many unfinished shipments each one already carries. */
    public List<DeliveryStaffOption> deliveryStaff() {
        return jdbc.query("""
                SELECT u.id,u.full_name,u.email,
                       (SELECT count(*) FROM shipments s WHERE s.assigned_staff_id = u.id
                          AND s.status IN ('ASSIGNED','IN_TRANSIT','DELIVERY_FAILED'))
                FROM users u
                WHERE u.role = 'DELIVERY' AND u.status = 'ACTIVE' AND u.verified_at IS NOT NULL
                ORDER BY u.full_name, u.id
                """, (rs, n) -> new DeliveryStaffOption(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
    }

    @Transactional
    public StaffOrderView assign(long managerId, long orderId, long deliveryStaffId) {
        String[] order = queries.lockOrder(orderId);
        if (!FulfillmentQueries.ASSIGNABLE.contains(order[0])) throw error(HttpStatus.CONFLICT, "ORDER_NOT_ASSIGNABLE",
                "Only packed orders that have not left the warehouse, or failed deliveries, can be assigned");
        record Shipment(long id, String status, Long staffId) {}
        Shipment shipment = jdbc.query("SELECT id,status,assigned_staff_id FROM shipments WHERE order_id = ? FOR UPDATE",
                        (rs, n) -> new Shipment(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class)), orderId)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "ORDER_NOT_ASSIGNABLE", "The order has no shipment yet"));
        if (!Set.of("READY", "ASSIGNED", "DELIVERY_FAILED").contains(shipment.status()))
            throw error(HttpStatus.CONFLICT, "ORDER_NOT_ASSIGNABLE", "The shipment is already on its way or finished");
        Boolean shipper = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM users WHERE id = ? AND role = 'DELIVERY' AND status = 'ACTIVE'
                               AND verified_at IS NOT NULL)
                """, Boolean.class, deliveryStaffId);
        if (!Boolean.TRUE.equals(shipper)) throw error(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_DELIVERY_STAFF",
                "The selected account is not an active Delivery Staff member");
        if ("DELIVERY_FAILED".equals(shipment.status())) {
            Integer failures = jdbc.queryForObject(
                    "SELECT count(*) FROM delivery_attempts WHERE shipment_id = ? AND status = 'FAILED'",
                    Integer.class, shipment.id());
            if (failures != null && failures >= store.intSetting("max_delivery_attempts", 2))
                throw error(HttpStatus.CONFLICT, "MAX_ATTEMPTS_REACHED", "The shipment used all delivery attempts");
            // A failed parcel keeps its status; the new shipper restarts delivery from the failed state.
            jdbc.update("UPDATE shipments SET assigned_staff_id = ?, assigned_at = now(), updated_at = now() WHERE id = ?",
                    deliveryStaffId, shipment.id());
        } else {
            jdbc.update("""
                    UPDATE shipments SET assigned_staff_id = ?, status = 'ASSIGNED', assigned_at = now(), updated_at = now()
                    WHERE id = ?
                    """, deliveryStaffId, shipment.id());
            jdbc.update("UPDATE orders SET status = 'ASSIGNED', updated_at = now() WHERE id = ?", orderId);
        }
        String detail = (shipment.staffId() == null ? "assigned to " : "reassigned from " + shipment.staffId() + " to ")
                + deliveryStaffId;
        store.audit(managerId, "ORDER_ASSIGNED", orderId, detail);
        return queries.staffView(orderId);
    }
}
