package com.osmshop.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmshop.auth.AuthService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Test data builders for the checkout and payment flows. Every test rolls back. */
@SpringBootTest(properties = {"management.health.mail.enabled=false", "app.payment-expiry.enabled=false"})
@AutoConfigureMockMvc
@Transactional
abstract class OrderTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;
    ObjectMapper json = new ObjectMapper();

    record Session(long id, String bearer) {}

    ResultActions place(Session session, long addressId, String method, String voucher) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("addressId", addressId, "paymentMethod", method));
        if (voucher != null) request.put("voucherCode", voucher);
        return mvc.perform(post("/api/orders").header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }

    long placedOrderId(Session session, long addressId, String method) throws Exception {
        String body = place(session, addressId, method, null).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    Session session(String role) {
        String email = "order-" + UUID.randomUUID() + "@example.com";
        Long userId = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,'x','Test User',?,'ACTIVE',now()) RETURNING id
                """, Long.class, email, role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 hour')",
                userId, AuthService.hash(token));
        return new Session(userId, "Bearer " + token);
    }

    long address(long userId) {
        return jdbc.queryForObject("""
                INSERT INTO user_addresses(user_id,recipient_name,phone,address_line,ward,district,province,is_default)
                VALUES (?,'Người nhận','0901234567','1 Lê Lợi','Bến Nghé','Quận 1','TP. Hồ Chí Minh',false)
                RETURNING id
                """, Long.class, userId);
    }

    long product(int price, int onHand, String status) {
        Long category = jdbc.queryForObject(
                "INSERT INTO categories(name) VALUES (?) RETURNING id", Long.class, "Order test " + UUID.randomUUID());
        return jdbc.queryForObject("""
                INSERT INTO products(category_id,sku,name,price,stock_on_hand,status)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, category, "SKU-" + UUID.randomUUID(), "Test product", price, onHand, status);
    }

    void cart(long userId, Map<Long, Integer> items) {
        Long cartId = jdbc.queryForObject("INSERT INTO carts(user_id) VALUES (?) RETURNING id", Long.class, userId);
        items.forEach((productId, quantity) -> jdbc.update(
                "INSERT INTO cart_items(cart_id,product_id,quantity) VALUES (?,?,?)", cartId, productId, quantity));
    }

    String voucher(String type, int value, Integer maxDiscount, int minOrder, int usageLimit) {
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        jdbc.update("""
                INSERT INTO vouchers(code,discount_type,discount_value,max_discount,min_order_amount,usage_limit,
                                     valid_from,valid_until,status)
                VALUES (?,?,?,?,?,?,now() - interval '1 day',now() + interval '1 day','ACTIVE')
                """, code, type, value, maxDiscount, minOrder, usageLimit);
        return code;
    }

    /** An unrelated order, used to occupy a voucher's usage limit. */
    long otherOrder() {
        Session someone = session("CUSTOMER");
        return jdbc.queryForObject("""
                INSERT INTO orders(order_code,customer_id,status,payment_method,recipient_name,recipient_phone,
                                   shipping_address,subtotal,total_amount)
                VALUES (?,?,'PENDING_CONFIRMATION','COD','X','0900000000','X',0,0) RETURNING id
                """, Long.class, "T-" + UUID.randomUUID(), someone.id());
    }

    long latestAttempt(long orderId) {
        return jdbc.queryForObject(
                "SELECT id FROM payment_attempts WHERE order_id=? ORDER BY attempt_number DESC LIMIT 1",
                Long.class, orderId);
    }

    void expireLatestAttempt(long orderId) {
        jdbc.update("""
                UPDATE payment_attempts SET created_at = now() - interval '1 hour', expires_at = now() - interval '1 minute'
                WHERE id = ?
                """, latestAttempt(orderId));
    }

    int reserved(long productId) {
        return jdbc.queryForObject("SELECT stock_reserved FROM products WHERE id=?", Integer.class, productId);
    }

    int onHand(long productId) {
        return jdbc.queryForObject("SELECT stock_on_hand FROM products WHERE id=?", Integer.class, productId);
    }

    String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId);
    }

    String voucherUsage(long orderId) {
        return jdbc.queryForObject("SELECT status FROM voucher_usages WHERE order_id=?", String.class, orderId);
    }
}
