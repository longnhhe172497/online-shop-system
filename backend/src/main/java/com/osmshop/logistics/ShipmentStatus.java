package com.osmshop.logistics;

import java.util.EnumSet;
import java.util.Set;

public enum ShipmentStatus {
    PENDING_PICKUP, PACKED, READY_FOR_DISPATCH, SHIPPING, DELIVERED, DELIVERY_FAILED, RETURNED_TO_WAREHOUSE;

    /** Trạng thái mặc định hiển thị trong hàng đợi giao hàng. */
    public static final Set<ShipmentStatus> DELIVERY_QUEUE =
            EnumSet.of(PENDING_PICKUP, READY_FOR_DISPATCH, SHIPPING);

    /** Các bước chuyển trạng thái hợp lệ do nhân viên giao hàng thực hiện. */
    public boolean canMoveTo(ShipmentStatus next) {
        return switch (next) {
            case SHIPPING -> this == PENDING_PICKUP || this == PACKED
                    || this == READY_FOR_DISPATCH || this == DELIVERY_FAILED;
            case DELIVERED, DELIVERY_FAILED -> this == SHIPPING;
            default -> false;
        };
    }
}
