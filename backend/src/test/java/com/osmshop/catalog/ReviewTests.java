package com.osmshop.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.osmshop.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** UC-CUS-015 (Rate / Review Product) and UC-MNG-005 (Manage Feedback). Every test rolls back. */
@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ReviewTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private record User(long id, String bearer) {}

    @Test
    void customerReviewsADeliveredItemOnceAndCanEditIt() throws Exception {
        long product = product("ACTIVE");
        User customer = user("CUSTOMER", "Phạm Văn Đức");
        long item = orderItem(customer, product, "DELIVERED");

        mvc.perform(get("/api/order-items/" + item + "/review").header("Authorization", customer.bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        submit(customer, item, 5, "  Màu rất đẹp  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Màu rất đẹp"))
                .andExpect(jsonPath("$.status").value("VISIBLE"))
                .andExpect(jsonPath("$.productId").value(product));

        // MSG-035: a second submit is blocked; the customer edits instead.
        submit(customer, item, 1, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
        mvc.perform(patch("/api/order-items/" + item + "/review").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":3,\"comment\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(3))
                .andExpect(jsonPath("$.comment").doesNotExist());
        mvc.perform(get("/api/order-items/" + item + "/review").header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(3));
    }

    @Test
    void onlyTheOwnerOfADeliveredOrderCanReview() throws Exception {
        long product = product("ACTIVE");
        User customer = user("CUSTOMER", "Khách A");
        User stranger = user("CUSTOMER", "Khách B");
        long confirmed = orderItem(customer, product, "CONFIRMED");
        long delivered = orderItem(customer, product, "DELIVERED");

        // MSG-034
        submit(customer, confirmed, 5, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
        // Someone else's item looks exactly like a missing one.
        submit(stranger, delivered, 5, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_ITEM_NOT_FOUND"));
        mvc.perform(patch("/api/order-items/" + delivered + "/review").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        submit(customer, delivered, 6, null).andExpect(status().isBadRequest());
        submit(customer, delivered, 4, "x".repeat(2001)).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM product_reviews WHERE order_item_id IN (?,?)",
                Integer.class, confirmed, delivered));
    }

    @Test
    void productPageShowsOnlyVisibleReviewsWithShortNames() throws Exception {
        long product = product("ACTIVE");
        User manager = user("MANAGER", "Quản lý");
        User first = user("CUSTOMER", "Phạm Văn Đức");
        User second = user("CUSTOMER", "Lan");
        submit(first, orderItem(first, product, "DELIVERED"), 4, "Vải mềm").andExpect(status().isCreated());
        long hiddenId = id(submit(second, orderItem(second, product, "DELIVERED"), 1, "Spam")
                .andExpect(status().isCreated()));

        moderate(manager, hiddenId, "{\"status\":\"HIDDEN\"}").andExpect(status().isOk());

        mvc.perform(get("/api/products/" + product + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].authorName").value("Phạm Đ."))
                .andExpect(jsonPath("$.items[0].rating").value(4))
                .andExpect(jsonPath("$.items[0].comment").value("Vải mềm"))
                .andExpect(jsonPath("$.items[0].customerEmail").doesNotExist());
        mvc.perform(get("/api/products/" + product))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.avgRating").value(4.0));

        // An unpublished product has no public reviews.
        long draft = product("DRAFT");
        mvc.perform(get("/api/products/" + draft + "/reviews")).andExpect(status().isNotFound());
    }

    @Test
    void managerHidesRestoresAndRepliesAndEveryStepIsAudited() throws Exception {
        long product = product("ACTIVE");
        User manager = user("MANAGER", "Quản lý");
        User customer = user("CUSTOMER", "Trần Bảo");
        long reviewId = id(submit(customer, orderItem(customer, product, "DELIVERED"), 2, "Hơi rộng")
                .andExpect(status().isCreated()));

        moderate(manager, reviewId, "{}").andExpect(status().isBadRequest());
        moderate(manager, reviewId, "{\"status\":\"REMOVED\"}").andExpect(status().isBadRequest());
        moderate(manager, -1, "{\"status\":\"HIDDEN\"}").andExpect(status().isNotFound());

        moderate(manager, reviewId, "{\"status\":\"HIDDEN\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.customerName").value("Trần Bảo"));
        mvc.perform(get("/api/manager/reviews").param("status", "HIDDEN").param("q", "Trần Bảo")
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(reviewId));

        moderate(manager, reviewId, "{\"status\":\"VISIBLE\",\"reply\":\"Cảm ơn bạn, shop sẽ tư vấn size kỹ hơn.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VISIBLE"))
                .andExpect(jsonPath("$.managerReply").value("Cảm ơn bạn, shop sẽ tư vấn size kỹ hơn."));
        mvc.perform(get("/api/products/" + product + "/reviews"))
                .andExpect(jsonPath("$.items[0].managerReply").value("Cảm ơn bạn, shop sẽ tư vấn size kỹ hơn."));

        moderate(manager, reviewId, "{\"reply\":\"  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.managerReply").doesNotExist());

        assertEquals(4, jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE entity_type = 'REVIEW' AND entity_id = ?
                  AND action IN ('REVIEW_HIDDEN','REVIEW_RESTORED','REVIEW_REPLIED','REVIEW_REPLY_REMOVED')
                """, Integer.class, Long.toString(reviewId)));
        // The customer's own view still works after moderation.
        assertEquals("VISIBLE", jdbc.queryForObject("SELECT status FROM product_reviews WHERE id = ?",
                String.class, reviewId));
    }

    @Test
    void shortNameKeepsTheFirstWordAndTheLastInitial() {
        assertEquals("Phạm Đ.", ReviewService.shortName("Phạm Văn Đức"));
        assertEquals("Lan", ReviewService.shortName("  Lan "));
        assertEquals("Khách hàng", ReviewService.shortName(" "));
    }

    private ResultActions submit(User user, long orderItemId, int rating, String comment) throws Exception {
        String body = comment == null ? "{\"rating\":" + rating + "}"
                : "{\"rating\":" + rating + ",\"comment\":\"" + comment + "\"}";
        return mvc.perform(post("/api/order-items/" + orderItemId + "/review").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions moderate(User manager, long reviewId, String body) throws Exception {
        return mvc.perform(patch("/api/manager/reviews/" + reviewId).header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long product(String status) {
        long category = jdbc.queryForObject("INSERT INTO categories(name,status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, "Review test " + UUID.randomUUID());
        return jdbc.queryForObject("""
                INSERT INTO products(category_id,sku,name,price,stock_on_hand,status)
                VALUES (?,?,'Áo review',100000,5,?) RETURNING id
                """, Long.class, category, "RV-" + UUID.randomUUID(), status);
    }

    /** One order with a single line, in the given status. */
    private long orderItem(User customer, long productId, String orderStatus) {
        long order = jdbc.queryForObject("""
                INSERT INTO orders(order_code,customer_id,status,payment_method,recipient_name,recipient_phone,
                                   shipping_address,subtotal,total_amount,delivered_at)
                VALUES (?,?,?,'COD','Người nhận','0900000000','1 Lê Lợi',100000,100000,
                        CASE WHEN ? = 'DELIVERED' THEN now() END) RETURNING id
                """, Long.class, "RV-" + UUID.randomUUID(), customer.id(), orderStatus, orderStatus);
        return jdbc.queryForObject("""
                INSERT INTO order_items(order_id,product_id,sku_snapshot,name_snapshot,unit_price,quantity,line_total)
                VALUES (?,?,'RV','Áo review',100000,1,100000) RETURNING id
                """, Long.class, order, productId);
    }

    private User user(String role, String fullName) {
        long id = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,'x',?,?,'ACTIVE',now()) RETURNING id
                """, Long.class, "review-" + UUID.randomUUID() + "@example.com", fullName, role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 hour')",
                id, AuthService.hash(token));
        return new User(id, "Bearer " + token);
    }
}
