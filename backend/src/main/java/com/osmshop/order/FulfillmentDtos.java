package com.osmshop.order;

import com.osmshop.order.OrderDtos.OrderItemView;
import com.osmshop.order.OrderDtos.ShipmentTracking;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Staff-side views of orders: Support COD confirmation and Manager order management. */
public final class FulfillmentDtos {
    private FulfillmentDtos() {}

    /** paymentStatus is the latest payment attempt status; deliveryStaffName is set once a shipper is assigned. */
    public record StaffOrderSummary(long id, String orderCode, String status, String paymentMethod,
                                    String paymentStatus, String customerName, String recipientPhone,
                                    BigDecimal totalAmount, int itemCount, OffsetDateTime createdAt,
                                    String shipmentStatus, String deliveryStaffName) {}

    public record StaffOrderView(long id, String orderCode, String status, String paymentMethod,
                                 String paymentStatus, String customerName, String customerEmail,
                                 String recipientName, String recipientPhone, String shippingAddress,
                                 BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingFee,
                                 BigDecimal totalAmount, String voucherCode, String note,
                                 OffsetDateTime createdAt, OffsetDateTime confirmedAt, String confirmedBy,
                                 OffsetDateTime cancelledAt, List<OrderItemView> items, ShipmentTracking shipment,
                                 boolean assignable) {}

    /** activeShipments counts shipments the shipper still has to finish (assigned, in transit or failed). */
    public record DeliveryStaffOption(long id, String fullName, String email, int activeShipments) {}

    public record ConfirmRequest(@Size(max = 500) String note) {}

    public record SupportCancelRequest(@NotBlank @Size(max = 300) String reason) {}

    public record AssignRequest(@NotNull @Positive Long deliveryStaffId) {}
}
