package com.osmshop.logistics;

import com.osmshop.logistics.LogisticsDtos.HistoryView;
import com.osmshop.logistics.LogisticsDtos.OrderItemView;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentListItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LogisticsQueries {
    private static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";

    public record OrderInfo(long id, String orderCode, String recipientName, String recipientPhone,
                            String deliveryAddress, BigDecimal totalAmount, String paymentMethod,
                            String paymentStatus, String status, OffsetDateTime createdAt,
                            String internalNote, OffsetDateTime deliveredAt) {
        public OrderInfo(long id, String orderCode, String recipientName, String recipientPhone,
                         String deliveryAddress, BigDecimal totalAmount, String paymentMethod,
                         String paymentStatus, String status, OffsetDateTime createdAt) {
            this(id, orderCode, recipientName, recipientPhone, deliveryAddress, totalAmount,
                    paymentMethod, paymentStatus, status, createdAt, null, null);
        }
    }

    private record ItemRow(long orderId, OrderItemView item) {}

    private final JdbcTemplate jdbc;
    private final ShipmentStatusHistoryRepository histories;

    public LogisticsQueries(JdbcTemplate jdbc, ShipmentStatusHistoryRepository histories) {
        this.jdbc = jdbc;
        this.histories = histories;
    }

    public Map<Long, OrderInfo> orders(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<OrderInfo> rows = jdbc.query("""
                SELECT o.id, o.order_code, o.recipient_name, o.recipient_phone, o.shipping_address, o.total_amount,
                       o.payment_method,
                       COALESCE((SELECT pa.status FROM payment_attempts pa WHERE pa.order_id = o.id ORDER BY pa.attempt_number DESC LIMIT 1),
                                CASE WHEN o.payment_method = 'COD' THEN 'UNPAID' ELSE 'PAID' END),
                       o.status, o.created_at, o.internal_note, o.delivered_at
                FROM orders o WHERE o.id IN (""" + marks(ids.size()) + ")",
                (rs, row) -> new OrderInfo(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBigDecimal(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getObject(10, OffsetDateTime.class),
                        rs.getString(11), rs.getObject(12, OffsetDateTime.class)),
                ids.toArray());
        return rows.stream().collect(Collectors.toMap(OrderInfo::id, Function.identity()));
    }

    public Map<Long, List<OrderItemView>> items(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        List<ItemRow> rows = jdbc.query("""
                SELECT order_id, product_id, name_snapshot, sku_snapshot, quantity
                FROM order_items WHERE order_id IN (""" + marks(orderIds.size()) + ") ORDER BY id",
                (rs, row) -> new ItemRow(rs.getLong(1),
                        new OrderItemView(rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5))),
                orderIds.toArray());
        return rows.stream().collect(Collectors.groupingBy(ItemRow::orderId,
                Collectors.mapping(ItemRow::item, Collectors.toList())));
    }

    public ShipmentListItem listItem(Shipment s, OrderInfo o) {
        String tracking = s.getTrackingNumber() != null ? s.getTrackingNumber()
                : (o != null ? "TRK-" + o.orderCode() : null);
        List<ShipmentStatusHistory> attemptList = s.getId() == null ? List.of()
                : histories.findByShipmentIdOrderByIdAsc(s.getId());

        ShipmentStatusHistory lastAttempt = attemptList.isEmpty() ? null : attemptList.get(attemptList.size() - 1);

        // Lấy lý do thất bại nhất quán từ delivery_attempts khi vận đơn đang ở trạng thái DELIVERY_FAILED
        String latestReason = null;
        if (s.getStatus() == ShipmentStatus.DELIVERY_FAILED) {
            latestReason = s.getFailedReason();
            if (latestReason == null && lastAttempt != null) {
                latestReason = lastAttempt.getNote();
            }
        }

        // Lấy ghi chú giao hàng nhất quán từ orders.internal_note hoặc delivery_attempts
        String notes = s.getDeliveryNotes();
        if (notes == null && o != null && o.internalNote() != null) {
            notes = o.internalNote();
        }
        if (notes == null && lastAttempt != null) {
            notes = lastAttempt.getNote();
        }

        // Lấy thời điểm bắt đầu giao nhất quán từ delivery_attempts.started_at hoặc shipments.assigned_at
        OffsetDateTime dispatchedAt = s.getDispatchedAt();
        if (dispatchedAt == null && lastAttempt != null && lastAttempt.getStartedAt() != null) {
            dispatchedAt = lastAttempt.getStartedAt();
        }

        // Lấy thời điểm giao thành công nhất quán từ orders.delivered_at hoặc delivery_attempts.finished_at
        OffsetDateTime deliveredAt = null;
        if (s.getStatus() == ShipmentStatus.DELIVERED) {
            deliveredAt = s.getDeliveredAt();
            if (deliveredAt == null && o != null && o.deliveredAt() != null) {
                deliveredAt = o.deliveredAt();
            }
            if (deliveredAt == null && lastAttempt != null && lastAttempt.getFinishedAt() != null) {
                deliveredAt = lastAttempt.getFinishedAt();
            }
        }

        return new ShipmentListItem(s.getId(), s.getOrderId(),
                o != null ? o.orderCode() : "", tracking,
                s.getCarrierName(), s.getStatus(), s.getDeliveryStaffId(), s.getWarehouseStaffId(),
                o != null ? o.recipientName() : "", o != null ? o.recipientPhone() : "",
                o != null ? o.deliveryAddress() : "", o != null ? o.paymentMethod() : "COD",
                o != null ? o.totalAmount() : BigDecimal.ZERO,
                s.getCodAmount(), s.isCodCollected(), notes, latestReason,
                s.getCreatedAt(), dispatchedAt, deliveredAt);
    }

    public ShipmentDetailResponse detail(Shipment s) {
        OrderInfo order = orders(List.of(s.getOrderId())).get(s.getOrderId());
        List<OrderItemView> items = items(List.of(s.getOrderId())).getOrDefault(s.getOrderId(), List.of());
        List<HistoryView> history = s.getId() == null ? List.of()
                : histories.findByShipmentIdOrderByIdAsc(s.getId()).stream()
                .map(h -> new HistoryView(h.getId(), h.getPreviousStatus(), h.getNewStatus(), h.getNote(),
                        h.getUpdatedBy(), h.getCreatedAt()))
                .toList();
        return new ShipmentDetailResponse(listItem(s, order), items, history);
    }

    public String reportZone() {
        List<String> values = jdbc.query(
                "SELECT value_text FROM system_settings WHERE setting_key='reporting_time_zone'",
                (rs, row) -> rs.getString(1));
        return values.isEmpty() || values.get(0) == null || values.get(0).isBlank()
                ? DEFAULT_ZONE : values.get(0).trim();
    }

    public LocalDate today(String zone) {
        try {
            return LocalDate.now(ZoneId.of(zone));
        } catch (Exception e) {
            return LocalDate.now(ZoneId.of(DEFAULT_ZONE));
        }
    }

    private static String marks(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}