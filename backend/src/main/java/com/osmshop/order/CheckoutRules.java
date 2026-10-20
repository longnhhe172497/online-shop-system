package com.osmshop.order;

import com.osmshop.auth.AuthApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Pure checkout calculations shared by checkout, payment retry and vouchers. */
final class CheckoutRules {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CheckoutRules() {}

    record Line(long productId, String sku, String name, BigDecimal price, boolean sellable,
                int available, int quantity) {
        BigDecimal subtotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }

        String problem() {
            if (!sellable) return "PRODUCT_UNAVAILABLE";
            return quantity > available ? "INSUFFICIENT_STOCK" : null;
        }
    }

    record Voucher(long id, String code, String type, BigDecimal value, BigDecimal maxDiscount,
                   BigDecimal minOrderAmount, int usageLimit, boolean usable) {
        /** PERCENT is rounded down to whole VND and capped by maxDiscount; never more than the subtotal. */
        BigDecimal discountFor(BigDecimal subtotal) {
            BigDecimal discount = "PERCENT".equals(type)
                    ? subtotal.multiply(value).divide(HUNDRED, 0, RoundingMode.DOWN)
                    : value;
            if (maxDiscount != null) discount = discount.min(maxDiscount);
            return discount.min(subtotal).setScale(2, RoundingMode.DOWN);
        }
    }

    static BigDecimal subtotal(List<Line> lines) {
        return lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Splits the order discount across lines in proportion to their subtotal, rounded down to
     * whole đồng (VND has no minor unit), then hands the leftover to lines that still have room.
     * The per-line amounts always sum to the order discount and never exceed a line's subtotal.
     */
    static List<BigDecimal> allocate(BigDecimal discount, List<Line> lines) {
        BigDecimal subtotal = subtotal(lines);
        List<BigDecimal> shares = new ArrayList<>();
        if (discount.signum() == 0 || subtotal.signum() == 0) {
            lines.forEach(line -> shares.add(BigDecimal.ZERO));
            return shares;
        }
        BigDecimal leftover = discount;
        for (Line line : lines) {
            BigDecimal share = discount.multiply(line.subtotal()).divide(subtotal, 0, RoundingMode.DOWN).setScale(2);
            shares.add(share);
            leftover = leftover.subtract(share);
        }
        for (int i = 0; i < lines.size() && leftover.signum() > 0; i++) {
            BigDecimal extra = lines.get(i).subtotal().subtract(shares.get(i)).min(leftover);
            shares.set(i, shares.get(i).add(extra));
            leftover = leftover.subtract(extra);
        }
        return shares;
    }

    static String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return code.toString();
    }

    static AuthApiException error(HttpStatus status, String code, String detail) {
        return new AuthApiException(status, code, detail);
    }
}
