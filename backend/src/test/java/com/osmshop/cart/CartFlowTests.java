package com.osmshop.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.osmshop.auth.AuthService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CartFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    void guestCartUsesHttpOnlyCookieAndStoresOnlyItsHash() throws Exception {
        long product = product(category("ACTIVE"), 100000, 7, 2, "ACTIVE");

        var added = mvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(item(product, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.subtotal").value(200000.0))
                .andReturn();
        String setCookie = added.getResponse().getHeader("Set-Cookie");
        assertTrue(setCookie.startsWith("FORME_CART="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        String token = setCookie.split("[=;]", 3)[1];
        Cookie cart = new Cookie("FORME_CART", token);

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM carts WHERE guest_session_hash = ? AND status = 'ACTIVE'",
                Integer.class, AuthService.hash(token)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM carts WHERE guest_session_hash = ?", Integer.class, token));

        mvc.perform(get("/api/cart").cookie(cart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(get("/api/cart")).andExpect(jsonPath("$.itemCount").value(0));

        // A write that carries the cart cookie must also carry a CSRF token.
        mvc.perform(post("/api/cart/items").cookie(cart).contentType(MediaType.APPLICATION_JSON).content(item(product, 1)))
                .andExpect(status().isForbidden());
        Csrf csrf = csrf();
        mvc.perform(post("/api/cart/items").cookie(cart, csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(item(product, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void addingTwiceSumsQuantityAndStopsAtAvailableStock() throws Exception {
        String customer = session("CUSTOMER");
        long product = product(category("ACTIVE"), 100000, 7, 2, "ACTIVE"); // available 5

        add(customer, product, 2).andExpect(status().isOk());
        add(customer, product, 2).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(4));
        add(customer, product, 2).andExpect(status().isConflict());
        mvc.perform(get("/api/cart").header("Authorization", customer))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(4));
    }

    @Test
    void rejectsUnsellableMissingProductsAndBadQuantities() throws Exception {
        String customer = session("CUSTOMER");
        long draft = product(category("ACTIVE"), 100000, 5, 0, "DRAFT");
        long hiddenCategory = product(category("INACTIVE"), 100000, 5, 0, "ACTIVE");
        long ok = product(category("ACTIVE"), 100000, 5, 0, "ACTIVE");

        add(customer, draft, 1).andExpect(status().isConflict());
        add(customer, hiddenCategory, 1).andExpect(status().isConflict());
        add(customer, 999999999L, 1).andExpect(status().isNotFound());
        add(customer, ok, 0).andExpect(status().isBadRequest());
        add(customer, ok, 100).andExpect(status().isBadRequest());
    }

    @Test
    void customersCanOnlyChangeTheirOwnLines() throws Exception {
        String alice = session("CUSTOMER");
        String bob = session("CUSTOMER");
        long product = product(category("ACTIVE"), 50000, 10, 0, "ACTIVE");

        String body = add(alice, product, 1).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number itemId = JsonPath.read(body, "$.items[0].id");

        mvc.perform(patch("/api/cart/items/" + itemId).header("Authorization", bob)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/cart/items/" + itemId).header("Authorization", bob)).andExpect(status().isNotFound());

        mvc.perform(patch("/api/cart/items/" + itemId).header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(3));
        mvc.perform(patch("/api/cart/items/" + itemId).header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":11}")).andExpect(status().isConflict());
        mvc.perform(delete("/api/cart/items/" + itemId).header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(0));
        mvc.perform(delete("/api/cart/items/" + itemId).header("Authorization", alice)).andExpect(status().isNotFound());

        // Checkout reads exactly one ACTIVE cart per customer.
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM carts c JOIN user_sessions s ON s.user_id = c.user_id
                WHERE s.token_hash = ? AND c.status = 'ACTIVE'
                """, Integer.class, AuthService.hash(alice.substring(7))));
    }

    @Test
    void staffAccountsCannotUseTheCart() throws Exception {
        mvc.perform(get("/api/cart").header("Authorization", session("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/cart").header("Authorization", session("WAREHOUSE"))).andExpect(status().isForbidden());
    }

    @Test
    void flaggedLinesAreExcludedFromSubtotal() throws Exception {
        String customer = session("CUSTOMER");
        long cat = category("ACTIVE");
        long stays = product(cat, 100000, 10, 0, "ACTIVE");
        long retired = product(cat, 70000, 10, 0, "ACTIVE");
        long shrinking = product(cat, 30000, 10, 0, "ACTIVE");
        add(customer, stays, 1).andExpect(status().isOk());
        add(customer, retired, 1).andExpect(status().isOk());
        add(customer, shrinking, 3).andExpect(status().isOk());

        jdbc.update("UPDATE products SET status = 'INACTIVE' WHERE id = ?", retired);
        jdbc.update("UPDATE products SET stock_on_hand = 2 WHERE id = ?", shrinking);

        mvc.perform(get("/api/cart").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].warning").doesNotExist())
                .andExpect(jsonPath("$.items[1].warning").value("PRODUCT_UNAVAILABLE"))
                .andExpect(jsonPath("$.items[2].warning").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.items[2].availableQuantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(100000.0));
    }

    private org.springframework.test.web.servlet.ResultActions add(String auth, long productId, int quantity)
            throws Exception {
        return mvc.perform(post("/api/cart/items").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(item(productId, quantity)));
    }

    private static String item(long productId, int quantity) {
        return "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}";
    }

    private record Csrf(String token, Cookie cookie) {}

    private Csrf csrf() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        return new Csrf(token, new Cookie("XSRF-TOKEN", setCookie.split("[=;]", 3)[1]));
    }

    private long category(String status) {
        return jdbc.queryForObject("INSERT INTO categories(name,status) VALUES (?,?) RETURNING id",
                Long.class, "Cart test " + UUID.randomUUID(), status);
    }

    private long product(long categoryId, long price, int onHand, int reserved, String status) {
        return jdbc.queryForObject("""
                INSERT INTO products(category_id,sku,name,price,stock_on_hand,stock_reserved,status)
                VALUES (?,?,?,?,?,?,?) RETURNING id
                """, Long.class, categoryId, "CART-" + UUID.randomUUID(), "Cart item", price, onHand, reserved, status);
    }

    private String session(String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,?,?,?,'ACTIVE',now()) RETURNING id
                """, Long.class, "cart-" + UUID.randomUUID() + "@example.com",
                passwords.encode("Password123"), "Cart User", role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 hour')",
                id, AuthService.hash(token));
        return "Bearer " + token;
    }
}