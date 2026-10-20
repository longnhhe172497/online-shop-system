package com.osmshop.cart;

import com.osmshop.auth.AuthApiException;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.cart.CartDtos.AddItemRequest;
import com.osmshop.cart.CartDtos.CartView;
import com.osmshop.cart.CartDtos.UpdateItemRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Guests (FORME_CART cookie) and signed-in Customers. Staff accounts get 403. */
@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService carts;
    private final GuestCartCookie cookie;

    public CartController(CartService carts, GuestCartCookie cookie) {
        this.carts = carts;
        this.cookie = cookie;
    }

    @GetMapping
    public CartView get(@AuthenticationPrincipal AuthPrincipal user, HttpServletRequest request) {
        return carts.view(customerId(user), guestToken(user, request));
    }

    @PostMapping("/items")
    public ResponseEntity<CartView> add(@AuthenticationPrincipal AuthPrincipal user, HttpServletRequest request,
                                        @Valid @RequestBody AddItemRequest body) {
        CartService.Result result = carts.addItem(customerId(user), guestToken(user, request),
                request.getRemoteAddr(), body.productId(), body.quantity());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.issuedGuestToken() != null) {
            response.header(HttpHeaders.SET_COOKIE, cookie.issue(result.issuedGuestToken()).toString());
        }
        return response.body(result.cart());
    }

    @PatchMapping("/items/{id}")
    public CartView update(@AuthenticationPrincipal AuthPrincipal user, HttpServletRequest request,
                           @PathVariable long id, @Valid @RequestBody UpdateItemRequest body) {
        return carts.updateItem(customerId(user), guestToken(user, request), id, body.quantity());
    }

    @DeleteMapping("/items/{id}")
    public CartView remove(@AuthenticationPrincipal AuthPrincipal user, HttpServletRequest request,
                           @PathVariable long id) {
        return carts.removeItem(customerId(user), guestToken(user, request), id);
    }

    private static Long customerId(AuthPrincipal user) {
        if (user == null) return null;
        if (!"CUSTOMER".equals(user.role())) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "CART_CUSTOMER_ONLY",
                    "Chỉ tài khoản khách hàng mới dùng được giỏ hàng.");
        }
        return user.id();
    }

    private String guestToken(AuthPrincipal user, HttpServletRequest request) {
        return user == null ? cookie.read(request) : null;
    }
}