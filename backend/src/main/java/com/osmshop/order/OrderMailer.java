package com.osmshop.order;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Order receipts. Sent only after the order transaction commits, so a rolled-back checkout never
 * emails the customer; a mail failure is logged and never undoes the order.
 */
@Component
public class OrderMailer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderMailer.class);
    private static final String SIMULATION_NOTICE = "Thanh toán mô phỏng — không có tiền thật được chuyển.";
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final String mailFrom;
    private final String frontendBaseUrl;

    public OrderMailer(JdbcTemplate jdbc, JavaMailSender mail, @Value("${app.mail-from}") String mailFrom,
                       @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderEvent(OrderEvent event) {
        try {
            send(event);
        } catch (MailException | org.springframework.dao.DataAccessException exception) {
            LOGGER.warn("Order email {} for order {} was not sent ({})", event.kind(), event.orderId(),
                    exception.getClass().getSimpleName());
        }
    }

    void send(OrderEvent event) {
        record Order(String email, String fullName, String code, String method, BigDecimal subtotal,
                     BigDecimal discount, BigDecimal shipping, BigDecimal total, String address) {}
        Order order = jdbc.queryForObject("""
                SELECT u.email,u.full_name,o.order_code,o.payment_method,o.subtotal,o.discount_amount,
                       o.shipping_fee,o.total_amount,o.shipping_address
                FROM orders o JOIN users u ON u.id = o.customer_id WHERE o.id = ?
                """, (rs, row) -> new Order(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
                        rs.getString(9)), event.orderId());
        List<String> lines = jdbc.query("""
                SELECT name_snapshot,quantity,line_total FROM order_items WHERE order_id = ? ORDER BY id
                """, (rs, row) -> "- " + rs.getString(1) + " × " + rs.getInt(2) + ": " + vnd(rs.getBigDecimal(3)),
                event.orderId());
        boolean qr = "SIMULATED_QR".equals(order.method());
        String subject;
        String intro;
        switch (event.kind()) {
            case PLACED -> {
                subject = "FORME - Đã nhận đơn hàng " + order.code();
                intro = qr ? "Đơn hàng đang chờ thanh toán QR. " + SIMULATION_NOTICE
                        : "Đơn hàng thanh toán khi nhận hàng (COD) đang chờ cửa hàng xác nhận.";
            }
            case PAID -> {
                subject = "FORME - Đã thanh toán đơn hàng " + order.code();
                intro = "Thanh toán đã được ghi nhận, đơn hàng đã được xác nhận. " + SIMULATION_NOTICE;
            }
            default -> {
                subject = "FORME - Đơn hàng " + order.code() + " đã bị hủy";
                intro = "Đơn hàng đã bị hủy. Nếu đơn đã được thanh toán, cửa hàng sẽ xử lý hoàn tiền mô phỏng.";
            }
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(order.email());
        message.setSubject(subject);
        message.setText("Chào " + order.fullName() + ",\n\n" + intro + "\n\n"
                + "Mã đơn: " + order.code() + "\n" + String.join("\n", lines) + "\n\n"
                + "Tạm tính: " + vnd(order.subtotal()) + "\n"
                + "Giảm giá: " + vnd(order.discount()) + "\n"
                + "Phí giao hàng: " + vnd(order.shipping()) + "\n"
                + "Tổng cộng: " + vnd(order.total()) + "\n"
                + "Giao đến: " + order.address() + "\n\n"
                + "Xem đơn hàng: " + frontendBaseUrl + "/me/orders/" + event.orderId() + "\n\nFORME");
        mail.send(message);
    }

    private static String vnd(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " đ";
    }
}
