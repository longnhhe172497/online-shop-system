package com.osmshop.logistics;

public enum InventoryTransactionType {
    MANUAL_ADJUSTMENT,
    ORDER_PACKED,
    RETURN_RESTOCK,
    INBOUND_PURCHASE,
    OUTBOUND_ORDER,
    RESERVE_HOLD,
    RELEASE_HOLD,
    ADJUSTMENT;

    public String toDbValue() {
        return switch (this) {
            case ORDER_PACKED, OUTBOUND_ORDER -> "ORDER_PACKED";
            case RETURN_RESTOCK -> "RETURN_RESTOCK";
            default -> "MANUAL_ADJUSTMENT";
        };
    }

    public static InventoryTransactionType fromDbValue(String dbValue) {
        if (dbValue == null) return MANUAL_ADJUSTMENT;
        return switch (dbValue) {
            case "ORDER_PACKED" -> ORDER_PACKED;
            case "RETURN_RESTOCK" -> RETURN_RESTOCK;
            default -> MANUAL_ADJUSTMENT;
        };
    }
}