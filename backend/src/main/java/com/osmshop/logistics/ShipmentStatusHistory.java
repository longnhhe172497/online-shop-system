package com.osmshop.logistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "shipment_status_history")
public class ShipmentStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private ShipmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 40)
    private ShipmentStatus newStatus;

    @Column(name = "note")
    private String note;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ShipmentStatusHistory() {}

    public ShipmentStatusHistory(Long shipmentId, ShipmentStatus previousStatus, ShipmentStatus newStatus,
                                 String note, Long updatedBy) {
        this.shipmentId = shipmentId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.note = note;
        this.updatedBy = updatedBy;
    }

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getShipmentId() { return shipmentId; }
    public ShipmentStatus getPreviousStatus() { return previousStatus; }
    public ShipmentStatus getNewStatus() { return newStatus; }
    public String getNote() { return note; }
    public Long getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
