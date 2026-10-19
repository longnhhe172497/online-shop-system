package com.osmshop.cart;

import static com.osmshop.cart.CartDtos.MAX_LINE_QUANTITY;

import com.osmshop.auth.AuthApiException;
import com.osmshop.auth.AuthRateLimiter;
import com.osmshop.auth.AuthService;
import com.osmshop.cart.CartDtos.CartLine;
import com.osmshop.cart.CartDtos.CartView;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One ACTIVE cart per customer (read by checkout) or per guest token.
 * The cart never reserves stock; it only validates against available = on hand - reserved.
 */
@Service
public class CartService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int GUEST_CARTS_PER_HOUR = 100;
    private static final int MAX_TOKEN_LENGTH = 200;

    private final JdbcTemplate jdbc;
    private final AuthRateLimiter limiter;

    public CartService(JdbcTemplate jdbc, AuthRateLimiter limiter) {
        this.jdbc = jdbc;
        this.limiter = limiter;
    }

    /** issuedGuestToken is set only when a new guest cart was created and a cookie must be sent. */
    public record Result(CartView cart, String issuedGuestToken) {}

    @Transactional(readOnly = true)
    public CartView view(Long customerId, String guestToken) {
        Long cartId = findCart(customerId, guestToken, false);
        return cartId == null ? new CartView(List.of(), 0, BigDecimal.ZERO) : load(cartId);
    }

    @Transactional
    public Result addItem(Long customerId, String guestToken, String clientIp, long productId, int quantity) {
        Stock stock = stock(productId);
        if (!stock.sellable()) throw unavailable();

        String issued = null;
        Long cartId;
        if (customerId != null) {
            cartId = customerCart(customerId);
        } else {
            cartId = findCart(null, guestToken, true);
            if (cartId == null) {
                limiter.check("guest-cart", clientIp == null ? "unknown" : clientIp, GUEST_CARTS_PER_HOUR, 3600);
                issued = newToken();
                cartId = jdbc.queryForObject("INSERT INTO carts(guest_session_hash) VALUES (?) RETURNING id",
                        Long.class, AuthService.hash(issued));
            }
        }

        int current = jdbc.query("SELECT quantity FROM cart_items WHERE cart_id = ? AND product_id = ?",
                (rs, row) -> rs.getInt(1), cartId, productId).stream().findFirst().orElse(0);
        int total = current + quantity;
        if (total > MAX_LINE_QUANTITY) {
            throw error(HttpStatus.CONFLICT, "QUANTITY_LIMIT",
                    "Mỗi sản phẩm chỉ được đặt tối đa " + MAX_LINE_QUANTITY + " chiếc.");
        }
        if (total > stock.available()) throw insufficient(stock.available());

        jdbc.update("""
                INSERT INTO cart_items(cart_id, product_id, quantity) VALUES (?, ?, ?)
                ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now()
                """, cartId, productId, total);
        touch(cartId);
        return new Result(load(cartId), issued);
    }

    @Transactional
    public CartView updateItem(Long customerId, String guestToken, long itemId, int quantity) {
        Long cartId = findCart(customerId, guestToken, true);
        Item item = cartId == null ? null : jdbc.query(
                "SELECT product_id, quantity FROM cart_items WHERE id = ? AND cart_id = ?",
                (rs, row) -> new Item(rs.getLong(1), rs.getInt(2)), itemId, cartId).stream().findFirst().orElse(null);
        if (item == null) throw itemNotFound();

        // Lowering a quantity is always allowed, so customers can fix a flagged line.
        if (quantity > item.quantity()) {
            Stock stock = stock(item.productId());
            if (!stock.sellable()) throw unavailable();
            if (quantity > stock.available()) throw insufficient(stock.available());
        }
        jdbc.update("UPDATE cart_items SET quantity = ?, updated_at = now() WHERE id = ?", quantity, itemId);
        touch(cartId);
        return load(cartId);
    }

    @Transactional
    public CartView removeItem(Long customerId, String guestToken, long itemId) {
        Long cartId = findCart(customerId, guestToken, true);
        if (cartId == null || jdbc.update("DELETE FROM cart_items WHERE id = ? AND cart_id = ?", itemId, cartId) == 0) {
            throw itemNotFound();
        }
        touch(cartId);
        return load(cartId);
    }

    private Long findCart(Long customerId, String guestToken, boolean lock) {
        String forUpdate = lock ? " FOR UPDATE" : "";
        List<Long> ids;
        if (customerId != null) {
            ids = jdbc.queryForList("SELECT id FROM carts WHERE user_id = ? AND status = 'ACTIVE'" + forUpdate,
                    Long.class, customerId);
        } else if (guestToken != null && guestToken.length() <= MAX_TOKEN_LENGTH) {
            ids = jdbc.queryForList("SELECT id FROM carts WHERE guest_session_hash = ? AND status = 'ACTIVE'" + forUpdate,
                    Long.class, AuthService.hash(guestToken));
        } else {
            return null;
        }
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** Finds or creates the customer's ACTIVE cart; safe against double clicks and a checkout converting it. */
    private long customerCart(long customerId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            jdbc.update("""
                    INSERT INTO carts(user_id) VALUES (?)
                    ON CONFLICT (user_id) WHERE status = 'ACTIVE' AND user_id IS NOT NULL DO NOTHING
                    """, customerId);
            Long id = findCart(customerId, null, true);
            if (id != null) return id;
        }
        throw new IllegalStateException("Could not open an active cart");
    }

    private CartView load(long cartId) {
        List<CartLine> lines = jdbc.query("""
                SELECT ci.id, ci.product_id, p.sku, p.name, p.image_url, p.price, ci.quantity,
                       p.stock_on_hand - p.stock_reserved AS available,
                       (p.status = 'ACTIVE' AND c.status = 'ACTIVE') AS sellable
                FROM cart_items ci
                JOIN products p ON p.id = ci.product_id
                JOIN categories c ON c.id = p.category_id
                WHERE ci.cart_id = ?
                ORDER BY ci.created_at, ci.id
                """, (rs, row) -> {
                    int quantity = rs.getInt("quantity");
                    int available = rs.getInt("available");
                    BigDecimal price = rs.getBigDecimal("price");
                    String warning = !rs.getBoolean("sellable") ? "PRODUCT_UNAVAILABLE"
                            : quantity > available ? "INSUFFICIENT_STOCK" : null;
                    return new CartLine(rs.getLong("id"), rs.getLong("product_id"), rs.getString("sku"),
                            rs.getString("name"), rs.getString("image_url"), price, quantity,
                            Math.max(available, 0), price.multiply(BigDecimal.valueOf(quantity)), warning);
                }, cartId);
        BigDecimal subtotal = lines.stream().filter(line -> line.warning() == null)
                .map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int itemCount = lines.stream().mapToInt(CartLine::quantity).sum();
        return new CartView(lines, itemCount, subtotal);
    }

    private Stock stock(long productId) {
        return jdbc.query("""
                SELECT p.status = 'ACTIVE' AND c.status = 'ACTIVE', p.stock_on_hand - p.stock_reserved
                FROM products p JOIN categories c ON c.id = p.category_id
                WHERE p.id = ?
                """, (rs, row) -> new Stock(rs.getBoolean(1), rs.getInt(2)), productId)
                .stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
    }

    private void touch(long cartId) {
        jdbc.update("UPDATE carts SET updated_at = now() WHERE id = ?", cartId);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static AuthApiException unavailable() {
        return error(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Sản phẩm này hiện không còn bán.");
    }

    private static AuthApiException insufficient(int available) {
        return error(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Chỉ còn " + Math.max(available, 0) + " sản phẩm trong kho.");
    }

    private static AuthApiException itemNotFound() {
        return error(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "Không tìm thấy sản phẩm trong giỏ hàng");
    }

    private static AuthApiException error(HttpStatus status, String code, String message) {
        return new AuthApiException(status, code, message);
    }

    private record Stock(boolean sellable, int available) {}

    private record Item(long productId, int quantity) {}
}