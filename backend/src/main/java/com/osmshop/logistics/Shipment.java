package com.osmshop.logistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "shipments")
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "tracking_number", length = 100, unique = true)
    private String trackingNumber;

    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    @Column(name = "delivery_staff_id")
    private Long deliveryStaffId;

    @Column(name = "warehouse_staff_id")
    private Long warehouseStaffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShipmentStatus status = ShipmentStatus.PENDING_PICKUP;

    @Column(name = "cod_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal codAmount = BigDecimal.ZERO;

    @Column(name = "cod_collected", nullable = false)
    private boolean codCollected;

    @Column(name = "delivery_notes")
    private String deliveryNotes;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "failed_reason", length = 255)
    private String failedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public Long getDeliveryStaffId() { return deliveryStaffId; }
    public void setDeliveryStaffId(Long deliveryStaffId) { this.deliveryStaffId = deliveryStaffId; }
    public Long getWarehouseStaffId() { return warehouseStaffId; }
    public void setWarehouseStaffId(Long warehouseStaffId) { this.warehouseStaffId = warehouseStaffId; }
    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }
    public BigDecimal getCodAmount() { return codAmount; }
    public void setCodAmount(BigDecimal codAmount) { this.codAmount = codAmount; }
    public boolean isCodCollected() { return codCollected; }
    public void setCodCollected(boolean codCollected) { this.codCollected = codCollected; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public void setDeliveryNotes(String deliveryNotes) { this.deliveryNotes = deliveryNotes; }
    public OffsetDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(OffsetDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(OffsetDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getFailedReason() { return failedReason; }
    public void setFailedReason(String failedReason) { this.failedReason = failedReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
