package com.osmshop.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

class VoucherManagementTests extends OrderTestSupport {
    @Autowired OrderMailer mailer;

    @Test
    void managerMaintainsVouchers() throws Exception {
        Session manager = session("MANAGER");
        String code = "new-" + UUID.randomUUID().toString().substring(0, 8);
        String body = save(manager, post("/api/manager/vouchers"), request(code, "PERCENT", 10, 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code.toUpperCase()))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        save(manager, post("/api/manager/vouchers"), request(code.toUpperCase(), "FIXED", 10000, 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_CODE_EXISTS"));
        save(manager, post("/api/manager/vouchers"), request("BAD-PCT-" + id, "PERCENT", 150, 5))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VOUCHER_RULE_INVALID"));
        save(manager, post("/api/manager/vouchers"), request("x", "FIXED", 1000, 5))
                .andExpect(status().isBadRequest());

        save(manager, patch("/api/manager/vouchers/" + id), request(code, "FIXED", 25000, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("FIXED"))
                .andExpect(jsonPath("$.usageLimit").value(7));
        mvc.perform(get("/api/manager/vouchers?search=" + code).header("Authorization", manager.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        jdbc.update("INSERT INTO voucher_usages(voucher_id,order_id,status) VALUES (?,?,'CONSUMED')", id, otherOrder());
        save(manager, patch("/api/manager/vouchers/" + id), request(code, "FIXED", 25000, 0))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/manager/vouchers/" + id).header("Authorization", manager.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_IN_USE"));

        String unused = save(manager, post("/api/manager/vouchers"), request("UNUSED-" + id, "FIXED", 5000, 1))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/manager/vouchers/" + json.readTree(unused).get("id").asLong())
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/manager/vouchers/999999999").header("Authorization", manager.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void usageLimitCannotDropBelowUsage() throws Exception {
        Session manager = session("MANAGER");
        String code = voucher("FIXED", 10000, null, 0, 5);
        long id = jdbc.queryForObject("SELECT id FROM vouchers WHERE code=?", Long.class, code);
        jdbc.update("INSERT INTO voucher_usages(voucher_id,order_id,status) VALUES (?,?,'CONSUMED')", id, otherOrder());
        jdbc.update("INSERT INTO voucher_usages(voucher_id,order_id,status) VALUES (?,?,'RESERVED')", id, otherOrder());
        save(manager, patch("/api/manager/vouchers/" + id), request(code, "FIXED", 10000, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_LIMIT_BELOW_USAGE"));
    }

    @Test
    void voucherPermissionIsRequired() throws Exception {
        String body = json.writeValueAsString(request("PERM-" + UUID.randomUUID().toString().substring(0, 6), "FIXED", 1000, 1));
        for (String role : new String[] {"ADMIN", "SUPPORT", "CUSTOMER"}) {
            mvc.perform(get("/api/manager/vouchers").header("Authorization", session(role).bearer()))
                    .andExpect(status().isForbidden());
        }
        jdbc.update("DELETE FROM role_permissions WHERE role='MANAGER' AND permission_code='MANAGER_VOUCHER_MANAGE'");
        mvc.perform(post("/api/manager/vouchers").header("Authorization", session("MANAGER").bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void receiptEmailIncludesOrderAndSimulationNotice() throws Exception {
        Session customer = session("CUSTOMER");
        long product = product(250000, 5, "ACTIVE");
        cart(customer.id(), Map.of(product, 2));
        long orderId = placedOrderId(customer, address(customer.id()), "SIMULATED_QR");

        mailer.send(new OrderEvent(orderId, OrderEvent.Kind.PLACED));
        org.mockito.ArgumentCaptor<SimpleMailMessage> sent = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        org.junit.jupiter.api.Assertions.assertTrue(sent.getValue().getSubject().startsWith("FORME - Đã nhận đơn hàng OD"));
        org.junit.jupiter.api.Assertions.assertTrue(sent.getValue().getText().contains("không có tiền thật được chuyển"));
        org.junit.jupiter.api.Assertions.assertTrue(sent.getValue().getText().contains("Tổng cộng: 530.000 đ"));
        verify(mail, org.mockito.Mockito.times(1)).send(any(SimpleMailMessage.class));
    }

    private ResultActions save(Session session, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                               Map<String, Object> request) throws Exception {
        return mvc.perform(builder.header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }

    private static Map<String, Object> request(String code, String type, int value, int usageLimit) {
        return Map.of("code", code, "discountType", type, "discountValue", value, "minOrderAmount", 0,
                "usageLimit", usageLimit, "validFrom", "2026-01-01T00:00:00+07:00",
                "validUntil", "2027-01-01T00:00:00+07:00", "status", "ACTIVE");
    }
}
