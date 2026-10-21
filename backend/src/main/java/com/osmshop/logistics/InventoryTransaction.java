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
@Table(name = "inventory_movements")
public class InventoryTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Convert(converter = TypeConverter.class)
    @Column(name = "movement_type", nullable = false, length = 30)
    private InventoryTransactionType transactionType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "stock_after", nullable = false)
    private int balanceAfter;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "reason", nullable = false, length = 500)
    private String notes;

    @Column(name = "performed_by")
    private Long staffId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Transient
    private String referenceId;

    protected InventoryTransaction() {}

    public InventoryTransaction(Long productId, InventoryTransactionType transactionType,
                                int quantityDelta, int balanceAfter, String referenceId,
                                String notes, Long staffId) {
        this(productId, transactionType, quantityDelta, balanceAfter, null, referenceId, notes, staffId);
    }

    public InventoryTransaction(Long productId, InventoryTransactionType transactionType,
                                int quantityDelta, int balanceAfter, Long orderItemId,
                                String referenceId, String notes, Long staffId) {
        this.productId = productId;
        this.transactionType = transactionType;
        this.quantityDelta = quantityDelta;
        this.balanceAfter = balanceAfter;
        this.orderItemId = orderItemId;
        this.referenceId = referenceId;
        this.notes = buildReason(referenceId, notes);
        this.staffId = staffId;
    }

    private static String buildReason(String ref, String note) {
        String r = ref == null || ref.isBlank() ? null : ref.trim();
        String n = note == null || note.isBlank() ? null : note.trim();
        if (r != null && n != null) return "[" + r + "] " + n;
        if (n != null) return n;
        if (r != null) return "Mã tham chiếu: " + r;
        return "Cập nhật tồn kho";
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (notes == null || notes.isBlank()) notes = "Cập nhật tồn kho";
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public InventoryTransactionType getTransactionType() { return transactionType; }
    public int getQuantityDelta() { return quantityDelta; }
    public int getBalanceAfter() { return balanceAfter; }
    public Long getOrderItemId() { return orderItemId; }
    public String getReferenceId() { return referenceId; }
    public String getNotes() { return notes; }
    public Long getStaffId() { return staffId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    @Converter
    public static class TypeConverter implements AttributeConverter<InventoryTransactionType, String> {
        @Override
        public String convertToDatabaseColumn(InventoryTransactionType attribute) {
            return attribute == null ? "MANUAL_ADJUSTMENT" : attribute.toDbValue();
        }

        @Override
        public InventoryTransactionType convertToEntityAttribute(String dbData) {
            return InventoryTransactionType.fromDbValue(dbData);
        }
    }
}