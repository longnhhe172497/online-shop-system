package com.osmshop.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.osmshop.auth.AuthService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CartMergeTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    void loginMergesGuestCartCapsAtStockAndClearsTheCookie() throws Exception {
        long category = category();
        long shirt = product(category, 7, 2);     // available 5
        long trousers = product(category, 10, 0);
        Customer customer = customer("CUSTOMER");
        mvc.perform(post("/api/cart/items").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(item(shirt, 4))).andExpect(status().isOk());

        Cookie cart = guestAdd(shirt, 2, null);
        guestAdd(trousers, 1, cart);

        List<String> cookies = browserLogin(customer.email(), cart).getResponse().getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(value -> value.startsWith("FORME_SESSION=")));
        assertTrue(cookies.stream().anyMatch(value -> value.startsWith("FORME_CART=;") && value.contains("Max-Age=0")));
        assertEquals("EXPIRED", guestStatus(cart));
        expectMergedCart(customer);

        // Logging in again with the old cookie changes nothing.
        browserLogin(customer.email(), cart);
        expectMergedCart(customer);
    }

    @Test
    void staffLoginDropsTheGuestCartWithoutCreatingOne() throws Exception {
        long shirt = product(category(), 5, 0);
        Customer manager = customer("MANAGER");
        Cookie cart = guestAdd(shirt, 1, null);

        browserLogin(manager.email(), cart);
        assertEquals("EXPIRED", guestStatus(cart));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM carts WHERE user_id = ?", Integer.class, manager.id()));
    }

    @Test
    void loginWithoutGuestCartLeavesCartCookieAlone() throws Exception {
        Customer customer = customer("CUSTOMER");
        MvcResult login = mvc.perform(post("/api/auth/browser-login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(customer.email())))
                .andExpect(status().isOk()).andReturn();
        assertTrue(login.getResponse().getHeaders("Set-Cookie").stream()
                .noneMatch(value -> value.startsWith("FORME_CART=")));
    }

    private void expectMergedCart(Customer customer) throws Exception {
        mvc.perform(get("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.items[1].quantity").value(1));
    }

    private Cookie guestAdd(long productId, int quantity, Cookie cart) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON).content(item(productId, quantity));
        if (cart != null) {
            Csrf csrf = csrf();
            request = request.cookie(cart, csrf.cookie()).header("X-XSRF-TOKEN", csrf.token());
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        if (cart != null) return cart;
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        return new Cookie("FORME_CART", setCookie.split("[=;]", 3)[1]);
    }

    private MvcResult browserLogin(String email, Cookie cart) throws Exception {
        Csrf csrf = csrf();
        return mvc.perform(post("/api/auth/browser-login").cookie(cart, csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email)))
                .andExpect(status().isOk()).andReturn();
    }

    private String guestStatus(Cookie cart) {
        return jdbc.queryForObject("SELECT status FROM carts WHERE guest_session_hash = ?", String.class,
                AuthService.hash(cart.getValue()));
    }

    private static String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"Password123\"}";
    }

    private static String item(long productId, int quantity) {
        return "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}";
    }

    private record Csrf(String token, Cookie cookie) {}

    private Csrf csrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        return new Csrf(token, new Cookie("XSRF-TOKEN", setCookie.split("[=;]", 3)[1]));
    }

    private long category() {
        return jdbc.queryForObject("INSERT INTO categories(name,status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, "Merge test " + UUID.randomUUID());
    }

    private long product(long categoryId, int onHand, int reserved) {
        return jdbc.queryForObject("""
                INSERT INTO products(category_id,sku,name,price,stock_on_hand,stock_reserved,status)
                VALUES (?,?,'Merge item',100000,?,?,'ACTIVE') RETURNING id
                """, Long.class, categoryId, "MERGE-" + UUID.randomUUID(), onHand, reserved);
    }

    private record Customer(long id, String email, String bearer) {}

    private Customer customer(String role) {
        String email = "merge-" + UUID.randomUUID() + "@example.com";
        Long id = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,?,'Merge User',?,'ACTIVE',now()) RETURNING id
                """, Long.class, email, passwords.encode("Password123"), role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 hour')",
                id, AuthService.hash(token));
        return new Customer(id, email, "Bearer " + token);
    }
}
