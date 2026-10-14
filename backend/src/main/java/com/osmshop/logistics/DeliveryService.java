package com.osmshop.logistics;

import com.osmshop.api.PageResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentListItem;
import com.osmshop.logistics.LogisticsDtos.UpdateShipmentStatusRequest;

public interface DeliveryService {
    enum Scope { MINE, UNASSIGNED, ALL }

    PageResponse<ShipmentListItem> list(long actorId, boolean admin, Scope scope, ShipmentStatus status,
                                        int page, int size);

    ShipmentDetailResponse get(long actorId, boolean admin, long shipmentId);

    ShipmentDetailResponse updateStatus(long actorId, boolean admin, long shipmentId,
                                        UpdateShipmentStatusRequest request);
}
