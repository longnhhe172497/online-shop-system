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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;

@Entity
@Table(name = "delivery_attempts")
public class ShipmentStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "attempt_number", nullable = false)
    private short attemptNumber = 1;

    @Column(name = "delivery_staff_id", nullable = false)
    private Long updatedBy;

    @Convert(converter = AttemptStatusConverter.class)
    @Column(name = "status", nullable = false, length = 25)
    private ShipmentStatus newStatus;

    @Column(name = "failure_reason", length = 500)
    private String note;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Transient
    private ShipmentStatus previousStatus;

    protected ShipmentStatusHistory() {}

    public ShipmentStatusHistory(Long shipmentId, ShipmentStatus previousStatus, ShipmentStatus newStatus,
                                 String note, Long updatedBy) {
        this(shipmentId, (short) 1, previousStatus, newStatus, note, updatedBy);
    }

    public ShipmentStatusHistory(Long shipmentId, short attemptNumber, ShipmentStatus previousStatus,
                                 ShipmentStatus newStatus, String note, Long updatedBy) {
        this.shipmentId = shipmentId;
        this.attemptNumber = attemptNumber;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.note = note;
        this.updatedBy = updatedBy;
        OffsetDateTime now = OffsetDateTime.now();
        this.startedAt = now;
        if (newStatus == ShipmentStatus.DELIVERED || newStatus == ShipmentStatus.DELIVERY_FAILED
                || newStatus == ShipmentStatus.RETURNED_TO_WAREHOUSE) {
            this.finishedAt = now;
        }
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (startedAt == null) startedAt = createdAt;
        if (newStatus == ShipmentStatus.DELIVERY_FAILED && (note == null || note.isBlank())) {
            note = "Giao hàng không thành công";
        }
    }

    public Long getId() { return id; }
    public Long getShipmentId() { return shipmentId; }
    public short getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(short attemptNumber) { this.attemptNumber = attemptNumber; }
    public ShipmentStatus getPreviousStatus() { return previousStatus; }
    public ShipmentStatus getNewStatus() { return newStatus; }
    public void setNewStatus(ShipmentStatus newStatus) { this.newStatus = newStatus; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    @Converter
    public static class AttemptStatusConverter implements AttributeConverter<ShipmentStatus, String> {
        @Override
        public String convertToDatabaseColumn(ShipmentStatus attribute) {
            if (attribute == null) return "ASSIGNED";
            return switch (attribute) {
                case SHIPPING, IN_TRANSIT -> "IN_TRANSIT";
                case DELIVERED -> "DELIVERED";
                case DELIVERY_FAILED -> "FAILED";
                case RETURNED_TO_WAREHOUSE -> "RETURNED";
                default -> "ASSIGNED";
            };
        }

        @Override
        public ShipmentStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) return ShipmentStatus.READY_FOR_DISPATCH;
            return switch (dbData) {
                case "IN_TRANSIT" -> ShipmentStatus.SHIPPING;
                case "DELIVERED" -> ShipmentStatus.DELIVERED;
                case "FAILED" -> ShipmentStatus.DELIVERY_FAILED;
                case "RETURNED" -> ShipmentStatus.RETURNED_TO_WAREHOUSE;
                default -> ShipmentStatus.READY_FOR_DISPATCH;
            };
        }
    }
}