package com.osmshop.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record PlaceOrderRequest(
            @NotNull @Positive Long addressId,
            @NotNull @Pattern(regexp = "SIMULATED_QR|COD") String paymentMethod,
            @Size(max = 60) String voucherCode) {}

    public record PreviewRequest(@Size(max = 60) String voucherCode) {}

    public record CancelRequest(@Size(max = 300) String reason) {}

    public record SimulateRequest(@NotNull @Pattern(regexp = "SUCCESS|FAILURE") String outcome) {}

    /** problem is null, PRODUCT_UNAVAILABLE or INSUFFICIENT_STOCK. */
    public record PreviewLine(long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                              int availableQuantity, BigDecimal lineSubtotal, String problem) {}

    public record CheckoutPreview(List<PreviewLine> items, BigDecimal subtotal, BigDecimal discountAmount,
                                  BigDecimal shippingFee, BigDecimal totalAmount, String voucherCode,
                                  boolean canPlaceOrder) {}

    public record OrderSummary(long id, String orderCode, String status, String paymentMethod,
                               BigDecimal totalAmount, int itemCount, OffsetDateTime createdAt) {}

    public record OrderItemView(long id, long productId, String sku, String name, BigDecimal unitPrice,
                                int quantity, BigDecimal discountAmount, BigDecimal lineTotal) {}

    /** Latest payment attempt. QR payments are simulated: no real money is transferred. */
    public record PaymentView(long id, int attemptNumber, String method, String status, BigDecimal amount,
                              String simulationCode, OffsetDateTime expiresAt, OffsetDateTime paidAt,
                              boolean simulated) {}

    /** Refunds are simulated as well. */
    public record RefundView(BigDecimal amount, String status, String reason, boolean simulated) {}

    public record OrderDetail(long id, String orderCode, String status, String paymentMethod,
                              String recipientName, String recipientPhone, String shippingAddress,
                              BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingFee,
                              BigDecimal totalAmount, String voucherCode, OffsetDateTime createdAt,
                              OffsetDateTime confirmedAt, OffsetDateTime deliveredAt, OffsetDateTime cancelledAt,
                              List<OrderItemView> items, PaymentView payment, RefundView refund,
                              int paymentAttemptsUsed, int maxPaymentAttempts, boolean cancellable,
                              boolean paymentRetryAllowed) {}
}
