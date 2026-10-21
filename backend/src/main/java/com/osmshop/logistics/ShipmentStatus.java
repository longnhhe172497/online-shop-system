package com.osmshop.logistics;

import java.util.List;
import java.util.Set;

public enum ShipmentStatus {
    PENDING_PICKUP,
    PACKED,
    READY_FOR_DISPATCH,
    SHIPPING,
    DELIVERED,
    DELIVERY_FAILED,
    RETURNED_TO_WAREHOUSE,
    // Các hằng số tương thích với schema V2
    READY,
    ASSIGNED,
    IN_TRANSIT;

    public static final List<ShipmentStatus> DELIVERY_QUEUE = List.of(
            PENDING_PICKUP, PACKED, READY_FOR_DISPATCH, SHIPPING, DELIVERY_FAILED,
            READY, ASSIGNED, IN_TRANSIT
    );

    public boolean canMoveTo(ShipmentStatus next) {
        if (next == null || this == next) return false;
        return switch (this) {
            case PENDING_PICKUP, PACKED, READY_FOR_DISPATCH, READY, ASSIGNED ->
                    Set.of(SHIPPING, IN_TRANSIT, READY_FOR_DISPATCH, ASSIGNED).contains(next);
            case SHIPPING, IN_TRANSIT ->
                    Set.of(DELIVERED, DELIVERY_FAILED, RETURNED_TO_WAREHOUSE).contains(next);
            case DELIVERY_FAILED ->
                    Set.of(SHIPPING, IN_TRANSIT, RETURNED_TO_WAREHOUSE).contains(next);
            case DELIVERED, RETURNED_TO_WAREHOUSE -> false;
        };
    }

    public String toDbValue() {
        return switch (this) {
            case PENDING_PICKUP, PACKED, READY -> "READY";
            case READY_FOR_DISPATCH, ASSIGNED -> "ASSIGNED";
            case SHIPPING, IN_TRANSIT -> "IN_TRANSIT";
            case DELIVERED -> "DELIVERED";
            case DELIVERY_FAILED -> "DELIVERY_FAILED";
            case RETURNED_TO_WAREHOUSE -> "RETURNED_TO_WAREHOUSE";
        };
    }

    public static ShipmentStatus fromDbValue(String dbData) {
        if (dbData == null) return PENDING_PICKUP;
        return switch (dbData) {
            case "READY", "PENDING_PICKUP", "PACKED" -> PENDING_PICKUP;
            case "ASSIGNED", "READY_FOR_DISPATCH" -> READY_FOR_DISPATCH;
            case "IN_TRANSIT", "SHIPPING" -> SHIPPING;
            case "DELIVERED" -> DELIVERED;
            case "DELIVERY_FAILED", "FAILED" -> DELIVERY_FAILED;
            case "RETURNED_TO_WAREHOUSE", "RETURNED" -> RETURNED_TO_WAREHOUSE;
            default -> PENDING_PICKUP;
        };
    }
}