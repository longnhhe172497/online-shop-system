package com.osmshop.logistics;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import com.osmshop.logistics.LogisticsDtos.OrderItemView;
import com.osmshop.logistics.LogisticsDtos.PackOrderRequest;
import com.osmshop.logistics.LogisticsDtos.PackingOrderView;
import com.osmshop.logistics.LogisticsDtos.ProductStockView;
import com.osmshop.logistics.LogisticsDtos.RestockRequest;
import com.osmshop.logistics.LogisticsDtos.RestockResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.TransactionView;
import com.osmshop.logistics.LogisticsQueries.OrderInfo;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseServiceImpl implements WarehouseService {
    /** Đơn đủ điều kiện để kho đóng gói theo chuẩn V2 schema. */
    private static final List<String> PACKABLE = List.of("CONFIRMED");
    private static final String SELECT_PRODUCT = """
            SELECT p.id, p.sku, p.name, p.price, p.stock_on_hand, p.stock_reserved, p.reorder_threshold, p.status
            FROM products p""";

    private record OrderRow(String code, String paymentMethod, String paymentStatus,
                            BigDecimal total, String status) {}
    private record Line(long orderItemId, long productId, int quantity) {}
    private record Stock(String sku, int onHand, int reserved) {}

    private final JdbcTemplate jdbc;
    private final ShipmentRepository shipments;
    private final ShipmentStatusHistoryRepository histories;
    private final InventoryTransactionRepository transactions;
    private final LogisticsQueries queries;

    public WarehouseServiceImpl(JdbcTemplate jdbc, ShipmentRepository shipments,
                                ShipmentStatusHistoryRepository histories,
                                InventoryTransactionRepository transactions, LogisticsQueries queries) {
        this.jdbc = jdbc;
        this.shipments = shipments;
        this.histories = histories;
        this.transactions = transactions;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductStockView> products(String search, boolean lowStockOnly, int page, int size) {
        validatePage(page, size);
        String term = "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT)) + "%";
        String filter = " WHERE p.status <> 'ARCHIVED' AND (lower(p.name) LIKE ? OR lower(p.sku) LIKE ?)"
                + " AND (? = false OR p.stock_on_hand - p.stock_reserved <= p.reorder_threshold)";
        Long total = jdbc.queryForObject("SELECT count(*) FROM products p" + filter, Long.class,
                term, term, lowStockOnly);
        List<ProductStockView> items = jdbc.query(SELECT_PRODUCT + filter + " ORDER BY p.id LIMIT ? OFFSET ?",
                WarehouseServiceImpl::mapProduct, term, term, lowStockOnly, size, page * size);
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    @Transactional
    public RestockResponse restock(long actorId, long productId, RestockRequest request) {
        List<Integer> current = jdbc.query(
                "SELECT stock_on_hand FROM products WHERE id=? AND status <> 'ARCHIVED' FOR UPDATE",
                (rs, row) -> rs.getInt(1), productId);
        if (current.isEmpty()) throw error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm.");
        long next = (long) current.get(0) + request.quantity();
        if (next > Integer.MAX_VALUE) throw error(HttpStatus.BAD_REQUEST, "QUANTITY_TOO_LARGE",
                "Số lượng tồn kho vượt giới hạn cho phép.");
        jdbc.update("UPDATE products SET stock_on_hand=?, updated_at=now() WHERE id=?", (int) next, productId);
        InventoryTransaction tx = transactions.save(new InventoryTransaction(productId,
                InventoryTransactionType.MANUAL_ADJUSTMENT, request.quantity(), (int) next,
                blankToNull(request.referenceId()), blankToNull(request.notes()), actorId));
        return new RestockResponse(product(productId), toView(tx));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionView> transactions(long productId, int page, int size) {
        validatePage(page, size);
        Long exists = jdbc.queryForObject("SELECT count(*) FROM products WHERE id=?", Long.class, productId);
        if (exists == null || exists == 0) throw error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
                "Không tìm thấy sản phẩm.");
        Page<InventoryTransaction> result = transactions.findByProductIdOrderByIdDesc(productId,
                PageRequest.of(page, size));
        return new PageResponse<>(result.getContent().stream().map(WarehouseServiceImpl::toView).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PackingOrderView> packingOrders(int page, int size) {
        validatePage(page, size);
        String from = " FROM orders o WHERE o.status = 'CONFIRMED'"
                + " AND NOT EXISTS (SELECT 1 FROM shipments s WHERE s.order_id = o.id)";
        Long total = jdbc.queryForObject("SELECT count(*)" + from, Long.class);
        List<Long> ids = jdbc.query("SELECT o.id" + from + " ORDER BY o.created_at, o.id LIMIT ? OFFSET ?",
                (rs, row) -> rs.getLong(1), size, page * size);
        Map<Long, OrderInfo> orders = queries.orders(ids);
        Map<Long, List<OrderItemView>> items = queries.items(ids);
        List<PackingOrderView> views = ids.stream().map(orders::get).map(o -> new PackingOrderView(o.id(),
                o.orderCode(), o.recipientName(), o.recipientPhone(), o.deliveryAddress(), o.totalAmount(),
                o.paymentMethod(), o.paymentStatus(), o.status(), o.createdAt(),
                items.getOrDefault(o.id(), List.of()))).toList();
        return new PageResponse<>(views, page, size, total == null ? 0 : total);
    }

    @Override
    @Transactional
    public ShipmentDetailResponse pack(long actorId, long orderId, PackOrderRequest request) {
        List<OrderRow> rows = jdbc.query("""
                SELECT o.order_code, o.payment_method,
                       COALESCE((SELECT pa.status FROM payment_attempts pa WHERE pa.order_id = o.id ORDER BY pa.attempt_number DESC LIMIT 1),
                                CASE WHEN o.payment_method = 'COD' THEN 'UNPAID' ELSE 'PAID' END),
                       o.total_amount, o.status
                FROM orders o WHERE o.id=? FOR UPDATE""",
                (rs, row) -> new OrderRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getString(5)), orderId);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.");
        OrderRow order = rows.get(0);
        if (!PACKABLE.contains(order.status())) throw error(HttpStatus.CONFLICT, "ORDER_NOT_PACKABLE",
                "Chỉ đóng gói được đơn ở trạng thái CONFIRMED (hiện tại: " + order.status() + ").");
        if (shipments.existsByOrderId(orderId)) throw error(HttpStatus.CONFLICT, "SHIPMENT_EXISTS",
                "Đơn hàng đã có vận đơn.");

        List<Line> lines = jdbc.query(
                "SELECT id, product_id, quantity FROM order_items WHERE order_id=? ORDER BY product_id, id",
                (rs, row) -> new Line(rs.getLong(1), rs.getLong(2), rs.getInt(3)), orderId);
        if (lines.isEmpty()) throw error(HttpStatus.CONFLICT, "ORDER_EMPTY", "Đơn hàng không có sản phẩm.");
        for (Line line : lines) {
            List<Stock> stock = jdbc.query("SELECT sku, stock_on_hand, stock_reserved FROM products WHERE id=? FOR UPDATE",
                    (rs, row) -> new Stock(rs.getString(1), rs.getInt(2), rs.getInt(3)), line.productId());
            if (stock.isEmpty()) throw error(HttpStatus.CONFLICT, "PRODUCT_NOT_FOUND",
                    "Sản phẩm #" + line.productId() + " không còn tồn tại.");
            Stock s = stock.get(0);
            if (s.onHand() < line.quantity()) throw error(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Không đủ tồn kho cho " + s.sku() + " (cần " + line.quantity() + ", còn " + s.onHand() + ").");
            int newOnHand = s.onHand() - line.quantity();
            int newReserved = Math.min(newOnHand, Math.max(0, s.reserved() - line.quantity()));
            jdbc.update("UPDATE products SET stock_on_hand=?, stock_reserved=?, updated_at=now() WHERE id=?",
                    newOnHand, newReserved, line.productId());
            transactions.save(new InventoryTransaction(line.productId(), InventoryTransactionType.ORDER_PACKED,
                    -line.quantity(), newOnHand, line.orderItemId(), order.code(),
                    "Xuất kho đóng gói đơn " + order.code(), actorId));
        }
        jdbc.update("""
                UPDATE stock_reservations SET status='CONSUMED', finished_at=now()
                WHERE order_item_id IN (SELECT id FROM order_items WHERE order_id=?) AND status='ACTIVE'""", orderId);
        jdbc.update("UPDATE orders SET status='READY_FOR_DELIVERY', updated_at=now() WHERE id=?", orderId);
        String packNote = request == null ? null : blankToNull(request.notes());
        if (packNote != null) {
            jdbc.update("UPDATE orders SET internal_note=?, updated_at=now() WHERE id=?", packNote, orderId);
        }
        boolean unpaidCod = "COD".equals(order.paymentMethod()) && !"PAID".equals(order.paymentStatus());
        Shipment shipment = new Shipment();
        shipment.setOrderId(orderId);
        shipment.setTrackingNumber("TRK-" + order.code());
        shipment.setCarrierName(request == null ? null : blankToNull(request.carrierName()));
        shipment.setDeliveryNotes(request == null ? null : blankToNull(request.notes()));
        shipment.setWarehouseStaffId(actorId);
        shipment.setStatus(ShipmentStatus.PENDING_PICKUP);
        shipment.setCodAmount(unpaidCod ? order.total() : BigDecimal.ZERO);
        shipment = shipments.saveAndFlush(shipment);
        return queries.detail(shipment);
    }

    private ProductStockView product(long id) {
        return jdbc.query(SELECT_PRODUCT + " WHERE p.id=?", WarehouseServiceImpl::mapProduct, id)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm."));
    }

    private static ProductStockView mapProduct(ResultSet rs, int row) throws SQLException {
        int onHand = rs.getInt("stock_on_hand");
        int reserved = rs.getInt("stock_reserved");
        int threshold = rs.getInt("reorder_threshold");
        int available = Math.max(0, onHand - reserved);
        return new ProductStockView(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                rs.getBigDecimal("price"), onHand, reserved, available, threshold,
                available <= threshold, rs.getString("status"));
    }

    private static TransactionView toView(InventoryTransaction t) {
        return new TransactionView(t.getId(), t.getProductId(), t.getTransactionType().name(),
                t.getQuantityDelta(), t.getBalanceAfter(), t.getReferenceId(), t.getNotes(),
                t.getStaffId(), t.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > 1_000_000)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Invalid page or size");
    }

    private static AuthApiException error(HttpStatus status, String code, String detail) {
        return new AuthApiException(status, code, detail);
    }
}