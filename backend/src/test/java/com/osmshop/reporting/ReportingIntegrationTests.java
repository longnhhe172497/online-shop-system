package com.osmshop.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osmshop.auth.AuthService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"management.health.mail.enabled=false", "app.payment-expiry.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ReportingIntegrationTests {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;
    private final ObjectMapper json = new ObjectMapper();

    private record Session(long id, String bearer) {}

    private Session session(String role) {
        Long id = jdbc.queryForObject("""
            INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
            VALUES (?, 'x', 'Report Tester', ?, 'ACTIVE', now()) RETURNING id
            """, Long.class, role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com", role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '1 hour')",
                id, AuthService.hash(token));
        return new Session(id, "Bearer " + token);
    }

    private long order(Session customer, int amount) {
        return jdbc.queryForObject("""
            INSERT INTO orders(order_code,customer_id,status,payment_method,recipient_name,recipient_phone,
                shipping_address,subtotal,discount_amount,shipping_fee,total_amount,created_at)
            VALUES (?,?,'DELIVERED','SIMULATED_QR','Customer','0900000000','Report address',?,0,0,?,now()) RETURNING id
            """, Long.class, "R-" + UUID.randomUUID(), customer.id(), amount, amount);
    }

    private org.springframework.test.web.servlet.ResultActions call(MockHttpServletRequestBuilder request,
                                                                     Session session) throws Exception {
        return mvc.perform(request.header("Authorization", session.bearer()));
    }

    @Test
    void summaryAggregatesRealOrdersPaidPaymentsAndCompletedRefunds() throws Exception {
        Session customer = session("CUSTOMER");
        Session manager = session("MANAGER");
        long orderId = order(customer, 125000);
        jdbc.update("""
            INSERT INTO payment_attempts(order_id,attempt_number,method,status,amount,simulation_code,expires_at,paid_at)
            VALUES (?,1,'SIMULATED_QR','PAID',?,?,now()+interval '15 minutes',now())
            """, orderId, 125000, "PAY-" + UUID.randomUUID());
        jdbc.update("""
            INSERT INTO refunds(order_id,reason,method,status,amount,idempotency_key,approved_by,completed_by,completed_at)
            VALUES (?,'CUSTOMER_RETURN','SIMULATED_QR','COMPLETED',25000,?,?,?,now())
            """, orderId, "REF-" + UUID.randomUUID(), manager.id(), manager.id());

        LocalDate today = LocalDate.now(REPORT_ZONE);
        String from = today.minusDays(1).toString();
        String to = today.plusDays(1).toString();
        String response = call(get("/api/manager/reports/summary?from=" + from + "&to=" + to), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.paidOrderCount").value(1))
                .andExpect(jsonPath("$.grossRevenue").value(125000))
                .andExpect(jsonPath("$.refundedAmount").value(25000))
                .andExpect(jsonPath("$.refundCount").value(1))
                .andReturn().getResponse().getContentAsString();

        JsonNode report = json.readTree(response);
        assertThat(report.get("daily").isArray()).isTrue();
        assertThat(report.get("daily").size()).isEqualTo(3);
        JsonNode todayMetric = null;
        for (JsonNode metric : report.get("daily")) {
            if (today.toString().equals(metric.get("date").asText())) todayMetric = metric;
        }
        assertThat(todayMetric).isNotNull();
        assertThat(todayMetric.get("orderCount").asLong()).isEqualTo(1);
        assertThat(todayMetric.get("paidOrderCount").asLong()).isEqualTo(1);
        assertThat(todayMetric.get("refundCount").asLong()).isEqualTo(1);
    }

    @Test
    void reportDateRangeIsValidatedAtHttpBoundary() throws Exception {
        Session manager = session("MANAGER");
        LocalDate today = LocalDate.now(REPORT_ZONE);
        call(get("/api/manager/reports/summary?from=" + today + "&to=" + today.minusDays(1)), manager)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_RANGE"));
    }
}
