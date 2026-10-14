package com.osmshop.logistics;

import com.osmshop.logistics.LogisticsDtos.HistoryView;
import com.osmshop.logistics.LogisticsDtos.OrderItemView;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentListItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Truy vấn đọc dùng chung (đơn hàng, dòng hàng) và lắp ráp response vận đơn. */
@Component
public class LogisticsQueries {
    public record OrderInfo(long id, String orderCode, String recipientName, String recipientPhone,
                            String deliveryAddress, BigDecimal totalAmount, String paymentMethod,
                            String paymentStatus, String status, OffsetDateTime createdAt) {}

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
                SELECT id,order_code,recipient_name,recipient_phone,delivery_address,total_amount,
                       payment_method,payment_status,status,created_at
                FROM orders WHERE id IN (""" + marks(ids.size()) + ")",
                (rs, row) -> new OrderInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBigDecimal(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getObject(10, OffsetDateTime.class)),
                ids.toArray());
        return rows.stream().collect(Collectors.toMap(OrderInfo::id, Function.identity()));
    }

    public Map<Long, List<OrderItemView>> items(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        List<ItemRow> rows = jdbc.query("""
                SELECT order_id,product_id,product_name,sku,quantity
                FROM order_items WHERE order_id IN (""" + marks(orderIds.size()) + ") ORDER BY id",
                (rs, row) -> new ItemRow(rs.getLong(1),
                        new OrderItemView(rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5))),
                orderIds.toArray());
        return rows.stream().collect(Collectors.groupingBy(ItemRow::orderId,
                Collectors.mapping(ItemRow::item, Collectors.toList())));
    }

    public ShipmentListItem listItem(Shipment s, OrderInfo o) {
        return new ShipmentListItem(s.getId(), s.getOrderId(), o.orderCode(), s.getTrackingNumber(),
                s.getCarrierName(), s.getStatus(), s.getDeliveryStaffId(), s.getWarehouseStaffId(),
                o.recipientName(), o.recipientPhone(), o.deliveryAddress(), o.paymentMethod(), o.totalAmount(),
                s.getCodAmount(), s.isCodCollected(), s.getDeliveryNotes(), s.getFailedReason(),
                s.getCreatedAt(), s.getDispatchedAt(), s.getDeliveredAt());
    }

    public ShipmentDetailResponse detail(Shipment s) {
        OrderInfo order = orders(List.of(s.getOrderId())).get(s.getOrderId());
        List<OrderItemView> items = items(List.of(s.getOrderId())).getOrDefault(s.getOrderId(), List.of());
        List<HistoryView> history = histories.findByShipmentIdOrderByIdAsc(s.getId()).stream()
                .map(h -> new HistoryView(h.getId(), h.getPreviousStatus(), h.getNewStatus(), h.getNote(),
                        h.getUpdatedBy(), h.getCreatedAt()))
                .toList();
        return new ShipmentDetailResponse(listItem(s, order), items, history);
    }

    private static String marks(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
