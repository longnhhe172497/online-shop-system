package com.osmshop.cart;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** HttpOnly cookie holding a random guest cart token. Only its SHA-256 hash is stored. */
@Component
public class GuestCartCookie {
    public static final String NAME = "FORME_CART";
    private static final Duration LIFETIME = Duration.ofDays(14);
    private final boolean secure;

    public GuestCartCookie(@Value("${app.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }

    public ResponseCookie issue(String token) {
        return build(token, LIFETIME);
    }

    /** Used after the guest cart is merged at login (next PR). */
    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/api").maxAge(age).build();
    }
}