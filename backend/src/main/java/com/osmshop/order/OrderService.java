package com.osmshop.order;

import static com.osmshop.order.CheckoutRules.error;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import com.osmshop.order.CheckoutRules.Line;
import com.osmshop.order.CheckoutRules.Voucher;
import com.osmshop.order.OrderDtos.CheckoutPreview;
import com.osmshop.order.OrderDtos.OrderDetail;
import com.osmshop.order.OrderDtos.OrderItemView;
import com.osmshop.order.OrderDtos.OrderSummary;
import com.osmshop.order.OrderDtos.PaymentView;
import com.osmshop.order.OrderDtos.PlaceOrderRequest;
import com.osmshop.order.OrderDtos.PreviewLine;
import com.osmshop.order.OrderDtos.RefundView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout turns the customer's ACTIVE cart into an order. Stock is only RESERVED here
 * (products.stock_reserved); on-hand stock is reduced once, when the warehouse packs the order.
 */
@Service
public class OrderService {
    static final Set<String> CANCELLABLE = Set.of("AWAITING_PAYMENT", "PENDING_CONFIRMATION", "CONFIRMED");
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final OrderStore store;
    private final VoucherService vouchers;

    public OrderService(JdbcTemplate jdbc, OrderStore store, VoucherService vouchers) {
        this.jdbc = jdbc;
        this.store = store;
        this.vouchers = vouchers;
    }

    /** Read-only quote of the current cart; stock problems are flagged per line instead of thrown. */
    public CheckoutPreview preview(long customerId, String voucherCode) {
        Long cartId = activeCart(customerId, false);
        List<Line> lines = cartId == null ? List.of() : cartLines(cartId, false);
        BigDecimal subtotal = CheckoutRules.subtotal(lines);
        BigDecimal shippingFee = lines.isEmpty() ? BigDecimal.ZERO : shippingFee();
        Voucher voucher = lines.isEmpty() || blank(voucherCode) ? null : vouchers.usableByCode(voucherCode, subtotal, false);
        BigDecimal discount = voucher == null ? BigDecimal.ZERO : voucher.discountFor(subtotal);
        List<PreviewLine> items = lines.stream().map(line -> new PreviewLine(line.productId(), line.sku(), line.name(),
                line.price(), line.quantity(), Math.max(line.available(), 0), line.subtotal(), line.problem())).toList();
        boolean ok = !lines.isEmpty() && lines.stream().allMatch(line -> line.problem() == null);
        return new CheckoutPreview(items, subtotal, discount, shippingFee, subtotal.subtract(discount).add(shippingFee),
                voucher == null ? null : voucher.code(), ok);
    }

    @Transactional
    public OrderDetail placeOrder(long customerId, PlaceOrderRequest request) {
        // Locking the cart makes a double-submitted checkout wait, then find no ACTIVE cart.
        Long cartId = activeCart(customerId, true);
        if (cartId == null) throw cartEmpty();
        Address address = jdbc.query("""
                SELECT recipient_name,phone,address_line,ward,district,province
                FROM user_addresses WHERE id=? AND user_id=?
                """, (rs, row) -> new Address(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)), request.addressId(), customerId)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Address not found"));

        List<Line> lines = cartLines(cartId, true);
        if (lines.isEmpty()) throw cartEmpty();
        requireSellable(lines);
        BigDecimal subtotal = CheckoutRules.subtotal(lines);
        Voucher voucher = blank(request.voucherCode()) ? null : vouchers.usableByCode(request.voucherCode(), subtotal, true);
        BigDecimal discount = voucher == null ? BigDecimal.ZERO : voucher.discountFor(subtotal);
        List<BigDecimal> lineDiscounts = CheckoutRules.allocate(discount, lines);
        BigDecimal shippingFee = shippingFee();
        BigDecimal total = subtotal.subtract(discount).add(shippingFee);
        boolean qr = "SIMULATED_QR".equals(request.paymentMethod());

        long orderId = jdbc.queryForObject("""
                INSERT INTO orders(order_code,customer_id,cart_id,voucher_id,status,payment_method,
                                   recipient_name,recipient_phone,shipping_address,
                                   subtotal,discount_amount,shipping_fee,total_amount)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, newOrderCode(), customerId, cartId, voucher == null ? null : voucher.id(),
                qr ? "AWAITING_PAYMENT" : "PENDING_CONFIRMATION", request.paymentMethod(),
                address.recipientName(), address.phone(), address.formatted(),
                subtotal, discount, shippingFee, total);
        // COD is paid only when Delivery Staff collects the cash.
        Long attemptId = qr ? createQrAttempt(orderId, 1, total) : null;
        if (!qr) jdbc.update("""
                INSERT INTO payment_attempts(order_id,attempt_number,method,status,amount)
                VALUES (?,1,'COD','UNPAID',?)
                """, orderId, total);

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            BigDecimal lineDiscount = lineDiscounts.get(i);
            long itemId = jdbc.queryForObject("""
                    INSERT INTO order_items(order_id,product_id,sku_snapshot,name_snapshot,unit_price,quantity,
                                            discount_amount,line_total)
                    VALUES (?,?,?,?,?,?,?,?) RETURNING id
                    """, Long.class, orderId, line.productId(), line.sku(), line.name(), line.price(),
                    line.quantity(), lineDiscount, line.subtotal().subtract(lineDiscount));
            reserve(itemId, line.productId(), line.quantity(), attemptId);
        }
        if (voucher != null) jdbc.update(
                "INSERT INTO voucher_usages(voucher_id,order_id,status) VALUES (?,?,'RESERVED')", voucher.id(), orderId);
        jdbc.update("UPDATE carts SET status='CONVERTED', updated_at=now() WHERE id=?", cartId);
        store.audit(customerId, "ORDER_PLACED", orderId, request.paymentMethod());
        store.publish(orderId, OrderEvent.Kind.PLACED);
        return getOrder(customerId, orderId);
    }

    @Transactional
    public OrderDetail cancel(long customerId, long orderId, String reason) {
        OrderStore.LockedOrder order = store.lockOrder(customerId, orderId);
        if (!CANCELLABLE.contains(order.status())) throw error(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE",
                "Order can only be cancelled before packing");
        Boolean paid = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM payment_attempts WHERE order_id = ? AND status = 'PAID')",
                Boolean.class, orderId);
        if (Boolean.TRUE.equals(paid)) {
            // Simulated refund; the after-sales stream completes it.
            jdbc.update("""
                    INSERT INTO refunds(order_id,reason,method,status,amount,idempotency_key,approved_by)
                    VALUES (?,'CANCELLED_PAID_ORDER',?,'PENDING',?,?,?)
                    """, orderId, "COD".equals(order.paymentMethod()) ? "MANUAL_COD" : "SIMULATED_QR",
                    order.totalAmount(), "CANCEL-ORDER-" + orderId, customerId);
        }
        String note = blank(reason) ? "Khách hủy đơn" : "Khách hủy đơn: " + reason.trim();
        store.cancel(orderId, customerId, note);
        return getOrder(customerId, orderId);
    }

    public PageResponse<OrderSummary> listOrders(long customerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_id=?", Long.class, customerId);
        List<OrderSummary> items = jdbc.query("""
                SELECT o.id,o.order_code,o.status,o.payment_method,o.total_amount,
                       (SELECT coalesce(sum(i.quantity),0) FROM order_items i WHERE i.order_id = o.id),o.created_at
                FROM orders o WHERE o.customer_id = ?
                ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new OrderSummary(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getInt(6), rs.getObject(7, OffsetDateTime.class)),
                customerId, size, (long) page * size);
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    /** Orders of other customers are reported as absent (404), never as forbidden. */
    public OrderDetail getOrder(long customerId, long orderId) {
        record Header(long id, String code, String status, String method, String name, String phone, String address,
                      BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, BigDecimal total, String voucher,
                      OffsetDateTime created, OffsetDateTime confirmed, OffsetDateTime delivered,
                      OffsetDateTime cancelled) {}
        Header order = jdbc.query("""
                SELECT o.id,o.order_code,o.status,o.payment_method,o.recipient_name,o.recipient_phone,
                       o.shipping_address,o.subtotal,o.discount_amount,o.shipping_fee,o.total_amount,v.code,
                       o.created_at,o.confirmed_at,o.delivered_at,o.cancelled_at
                FROM orders o LEFT JOIN vouchers v ON v.id = o.voucher_id
                WHERE o.id = ? AND o.customer_id = ?
                """, (rs, row) -> new Header(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getBigDecimal(9),
                        rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getString(12),
                        rs.getObject(13, OffsetDateTime.class), rs.getObject(14, OffsetDateTime.class),
                        rs.getObject(15, OffsetDateTime.class), rs.getObject(16, OffsetDateTime.class)),
                        orderId, customerId)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
        List<OrderItemView> items = jdbc.query("""
                SELECT id,product_id,sku_snapshot,name_snapshot,unit_price,quantity,discount_amount,line_total
                FROM order_items WHERE order_id = ? ORDER BY id
                """, (rs, row) -> new OrderItemView(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getInt(6), rs.getBigDecimal(7), rs.getBigDecimal(8)), orderId);
        record Latest(PaymentView view, boolean livePending) {}
        Latest latest = jdbc.query("""
                SELECT id,attempt_number,method,status,amount,simulation_code,expires_at,paid_at,
                       status = 'PENDING' AND expires_at > now()
                FROM payment_attempts WHERE order_id = ? ORDER BY attempt_number DESC LIMIT 1
                """, (rs, row) -> new Latest(new PaymentView(rs.getLong(1), rs.getInt(2), rs.getString(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class),
                        rs.getObject(8, OffsetDateTime.class), "SIMULATED_QR".equals(rs.getString(3))),
                        rs.getBoolean(9)), orderId)
                .stream().findFirst().orElse(null);
        RefundView refund = jdbc.query("""
                SELECT amount,status,reason FROM refunds WHERE order_id = ? ORDER BY created_at DESC, id DESC LIMIT 1
                """, (rs, row) -> new RefundView(rs.getBigDecimal(1), rs.getString(2), rs.getString(3), true), orderId)
                .stream().findFirst().orElse(null);
        int attemptsUsed = latest == null ? 0 : latest.view().attemptNumber();
        int maxAttempts = store.intSetting("max_qr_attempts", 3);
        boolean retryAllowed = "SIMULATED_QR".equals(order.method()) && "AWAITING_PAYMENT".equals(order.status())
                && (latest == null || !latest.livePending()) && attemptsUsed < maxAttempts;
        return new OrderDetail(order.id(), order.code(), order.status(), order.method(), order.name(), order.phone(),
                order.address(), order.subtotal(), order.discount(), order.shipping(), order.total(), order.voucher(),
                order.created(), order.confirmed(), order.delivered(), order.cancelled(), items,
                latest == null ? null : latest.view(), refund, attemptsUsed, maxAttempts,
                CANCELLABLE.contains(order.status()), retryAllowed);
    }

    // ---- shared with PaymentService ----

    /** Creates a PENDING QR attempt that expires after qr_expiry_minutes. */
    long createQrAttempt(long orderId, int attemptNumber, BigDecimal amount) {
        return jdbc.queryForObject("""
                INSERT INTO payment_attempts(order_id,attempt_number,method,status,amount,simulation_code,expires_at)
                VALUES (?,?,'SIMULATED_QR','PENDING',?,?,now() + ? * interval '1 minute') RETURNING id
                """, Long.class, orderId, attemptNumber, amount, "SIM-" + CheckoutRules.randomCode(12),
                store.intSetting("qr_expiry_minutes", 15));
    }

    /** A QR reservation ends with its payment attempt; a COD reservation lasts until packing or cancellation. */
    void reserve(long orderItemId, long productId, int quantity, Long attemptId) {
        jdbc.update("""
                INSERT INTO stock_reservations(order_item_id,payment_attempt_id,quantity,status,expires_at)
                VALUES (?,?,?,'ACTIVE',(SELECT expires_at FROM payment_attempts WHERE id = ?))
                """, orderItemId, attemptId, quantity, attemptId);
        jdbc.update("UPDATE products SET stock_reserved = stock_reserved + ?, updated_at = now() WHERE id = ?",
                quantity, productId);
    }

    static void requireSellable(List<Line> lines) {
        for (Line line : lines) {
            if ("PRODUCT_UNAVAILABLE".equals(line.problem())) throw error(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE",
                    "Product " + line.sku() + " is no longer sold");
            if ("INSUFFICIENT_STOCK".equals(line.problem())) throw error(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Only " + Math.max(line.available(), 0) + " of " + line.sku() + " available");
        }
    }

    private Long activeCart(long customerId, boolean lock) {
        List<Long> carts = jdbc.queryForList("SELECT id FROM carts WHERE user_id=? AND status='ACTIVE'"
                + (lock ? " FOR UPDATE" : ""), Long.class, customerId);
        return carts.isEmpty() ? null : carts.getFirst();
    }

    /** With lock=true, product rows are locked in id order so concurrent checkouts cannot oversell or deadlock. */
    private List<Line> cartLines(long cartId, boolean lock) {
        return jdbc.query("""
                SELECT p.id,p.sku,p.name,p.price,p.status = 'ACTIVE' AND c.status = 'ACTIVE',
                       p.stock_on_hand - p.stock_reserved,ci.quantity
                FROM cart_items ci
                JOIN products p ON p.id = ci.product_id
                JOIN categories c ON c.id = p.category_id
                WHERE ci.cart_id = ?
                ORDER BY p.id
                """ + (lock ? " FOR UPDATE OF p" : ""), (rs, row) -> new Line(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getBigDecimal(4), rs.getBoolean(5), rs.getInt(6), rs.getInt(7)), cartId);
    }

    private BigDecimal shippingFee() {
        return BigDecimal.valueOf(store.intSetting("shipping_fee", 0));
    }

    private static String newOrderCode() {
        return "OD" + LocalDate.now(SHOP_ZONE).format(CODE_DATE) + "-" + CheckoutRules.randomCode(6);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static AuthApiException cartEmpty() {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "CART_EMPTY", "Cart is empty");
    }

    private record Address(String recipientName, String phone, String addressLine, String ward,
                           String district, String province) {
        String formatted() {
            return Stream.of(addressLine, ward, district, province).filter(Objects::nonNull)
                    .filter(part -> !part.isBlank()).collect(Collectors.joining(", "));
        }
    }
}
