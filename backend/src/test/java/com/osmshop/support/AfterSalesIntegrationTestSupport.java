package com.osmshop.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmshop.auth.AuthService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"management.health.mail.enabled=false", "app.payment-expiry.enabled=false"})
@AutoConfigureMockMvc
@Transactional
abstract class AfterSalesIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;
    final ObjectMapper json = new ObjectMapper();

    record Session(long id, String bearer) {}
    record OrderFixture(long id, long itemId, long productId) {}

    Session session(String role) {
        String email = (role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        Long id = jdbc.queryForObject("""
            INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
            VALUES (?,'x','Integration Tester',?,'ACTIVE',now()) RETURNING id
            """, Long.class, email, role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 hour')",
                id, AuthService.hash(token));
        return new Session(id, "Bearer " + token);
    }

    OrderFixture deliveredOrder(Session customer, int quantity, int unitPrice) {
        Long category = jdbc.queryForObject("INSERT INTO categories(name) VALUES (?) RETURNING id", Long.class,
                "After-sales " + UUID.randomUUID());
        Long product = jdbc.queryForObject("""
            INSERT INTO products(category_id,sku,name,price,stock_on_hand,status)
            VALUES (?,?, 'Returnable item', ?, 10, 'ACTIVE') RETURNING id
            """, Long.class, category, "AS-" + UUID.randomUUID(), unitPrice);
        Long order = jdbc.queryForObject("""
            INSERT INTO orders(order_code,customer_id,status,payment_method,recipient_name,recipient_phone,
              shipping_address,subtotal,discount_amount,shipping_fee,total_amount,delivered_at)
            VALUES (?,?,'DELIVERED','SIMULATED_QR','Customer','0900000000','Test address',?,0,0,?,now()) RETURNING id
            """, Long.class, "AS-" + UUID.randomUUID(), customer.id(), unitPrice * quantity, unitPrice * quantity);
        Long item = jdbc.queryForObject("""
            INSERT INTO order_items(order_id,product_id,sku_snapshot,name_snapshot,unit_price,quantity,discount_amount,line_total)
            VALUES (?,?,?,'Returnable item',?,?,0,?) RETURNING id
            """, Long.class, order, product, "AS-" + UUID.randomUUID(), unitPrice, quantity, unitPrice * quantity);
        return new OrderFixture(order, item, product);
    }

    ResultActions call(MockHttpServletRequestBuilder request, Session session, Object body) throws Exception {
        request.header("Authorization", session.bearer());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }

    long idFrom(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }
}
