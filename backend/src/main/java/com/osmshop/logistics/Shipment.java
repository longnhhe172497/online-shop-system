package com.osmshop.logistics;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "shipments")
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "assigned_staff_id")
    private Long deliveryStaffId;

    @Convert(converter = StatusConverter.class)
    @Column(name = "status", nullable = false, length = 30)
    private ShipmentStatus status = ShipmentStatus.PENDING_PICKUP;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "cod_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal codAmount = BigDecimal.ZERO;

    @Column(name = "cod_collected_at")
    private OffsetDateTime codCollectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Các trường phục vụ DTO/UI nhưng dùng @Transient để không tạo cột thừa trong bảng shipments V2
    @Transient
    private String trackingNumber;

    @Transient
    private String carrierName;

    @Transient
    private Long warehouseStaffId;

    @Transient
    private String deliveryNotes;

    @Transient
    private OffsetDateTime dispatchedAt;

    @Transient
    private OffsetDateTime deliveredAt;

    @Transient
    private String failedReason;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }

    public Long getDeliveryStaffId() { return deliveryStaffId; }
    public void setDeliveryStaffId(Long deliveryStaffId) {
        this.deliveryStaffId = deliveryStaffId;
        if (deliveryStaffId != null && this.assignedAt == null) {
            this.assignedAt = OffsetDateTime.now();
        }
    }

    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }

    public Long getWarehouseStaffId() { return warehouseStaffId; }
    public void setWarehouseStaffId(Long warehouseStaffId) { this.warehouseStaffId = warehouseStaffId; }

    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }

    public BigDecimal getCodAmount() { return codAmount; }
    public void setCodAmount(BigDecimal codAmount) { this.codAmount = codAmount; }

    public boolean isCodCollected() { return codCollectedAt != null; }
    public void setCodCollected(boolean codCollected) {
        this.codCollectedAt = codCollected ? OffsetDateTime.now() : null;
    }

    public OffsetDateTime getCodCollectedAt() { return codCollectedAt; }
    public void setCodCollectedAt(OffsetDateTime codCollectedAt) { this.codCollectedAt = codCollectedAt; }

    public String getDeliveryNotes() { return deliveryNotes; }
    public void setDeliveryNotes(String deliveryNotes) { this.deliveryNotes = deliveryNotes; }

    public OffsetDateTime getDispatchedAt() { return dispatchedAt != null ? dispatchedAt : assignedAt; }
    public void setDispatchedAt(OffsetDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }

    public OffsetDateTime getDeliveredAt() { return deliveredAt != null ? deliveredAt : codCollectedAt; }
    public void setDeliveredAt(OffsetDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public String getFailedReason() { return failedReason; }
    public void setFailedReason(String failedReason) { this.failedReason = failedReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    @Converter
    public static class StatusConverter implements AttributeConverter<ShipmentStatus, String> {
        @Override
        public String convertToDatabaseColumn(ShipmentStatus attribute) {
            return attribute == null ? "READY" : attribute.toDbValue();
        }

        @Override
        public ShipmentStatus convertToEntityAttribute(String dbData) {
            return ShipmentStatus.fromDbValue(dbData);
        }
    }
}