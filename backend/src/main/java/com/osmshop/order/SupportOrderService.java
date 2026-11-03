package com.osmshop.order;

import static com.osmshop.order.CheckoutRules.error;

import com.osmshop.api.PageResponse;
import com.osmshop.order.FulfillmentDtos.StaffOrderView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support Staff confirm COD orders (UC-SUP-001) before the warehouse may pack them. Stock stays reserved
 * from checkout; payment stays UNPAID until the shipper collects the cash. An order the customer cannot
 * confirm (unreachable, wrong address…) is cancelled with a reason, which gives its stock and voucher back.
 */
@Service
public class SupportOrderService {
    private final JdbcTemplate jdbc;
    private final OrderStore store;
    private final FulfillmentQueries queries;

    public SupportOrderService(JdbcTemplate jdbc, OrderStore store, FulfillmentQueries queries) {
        this.jdbc = jdbc;
        this.store = store;
        this.queries = queries;
    }

    /** COD orders waiting for confirmation, oldest first. */
    public PageResponse<StaffOrderView> pending(int page, int size) {
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM orders WHERE payment_method = 'COD' AND status = 'PENDING_CONFIRMATION'
                """, Long.class);
        List<StaffOrderView> items = jdbc.queryForList("""
                SELECT id FROM orders WHERE payment_method = 'COD' AND status = 'PENDING_CONFIRMATION'
                ORDER BY created_at, id LIMIT ? OFFSET ?
                """, Long.class, size, (long) page * size).stream().map(queries::staffView).toList();
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    @Transactional
    public StaffOrderView confirm(long staffId, long orderId, String note) {
        requirePending(orderId);
        jdbc.update("""
                UPDATE orders SET status = 'CONFIRMED', confirmed_by = ?, confirmed_at = now(), updated_at = now(),
                                  internal_note = coalesce(?, internal_note)
                WHERE id = ?
                """, staffId, blank(note) ? null : note.trim(), orderId);
        store.audit(staffId, "ORDER_CONFIRMED", orderId, blank(note) ? "COD confirmed" : note.trim());
        store.publish(orderId, OrderEvent.Kind.CONFIRMED);
        return queries.staffView(orderId);
    }

    @Transactional
    public StaffOrderView cancel(long staffId, long orderId, String reason) {
        requirePending(orderId);
        store.cancel(orderId, staffId, "Hỗ trợ hủy đơn: " + reason.trim());
        return queries.staffView(orderId);
    }

    private void requirePending(long orderId) {
        String[] order = queries.lockOrder(orderId);
        if (!"PENDING_CONFIRMATION".equals(order[0]) || !"COD".equals(order[1])) throw error(HttpStatus.CONFLICT,
                "ORDER_NOT_PENDING_CONFIRMATION", "Only COD orders waiting for confirmation can be confirmed or cancelled");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
