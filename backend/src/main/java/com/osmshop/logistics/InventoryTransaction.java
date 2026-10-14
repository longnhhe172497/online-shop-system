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
@Table(name = "inventory_transactions")
public class InventoryTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private InventoryTransactionType transactionType;

    /** Dương khi nhập kho/hoàn trả, âm khi xuất kho giao đơn. */
    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InventoryTransaction() {}

    public InventoryTransaction(Long productId, InventoryTransactionType type, int quantityDelta,
                                int balanceAfter, String referenceId, String notes, Long staffId) {
        this.productId = productId;
        this.transactionType = type;
        this.quantityDelta = quantityDelta;
        this.balanceAfter = balanceAfter;
        this.referenceId = referenceId;
        this.notes = notes;
        this.staffId = staffId;
    }

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public InventoryTransactionType getTransactionType() { return transactionType; }
    public int getQuantityDelta() { return quantityDelta; }
    public int getBalanceAfter() { return balanceAfter; }
    public String getReferenceId() { return referenceId; }
    public String getNotes() { return notes; }
    public Long getStaffId() { return staffId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
