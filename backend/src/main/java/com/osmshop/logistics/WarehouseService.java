package com.osmshop.logistics;

import com.osmshop.api.PageResponse;
import com.osmshop.logistics.LogisticsDtos.PackOrderRequest;
import com.osmshop.logistics.LogisticsDtos.PackingOrderView;
import com.osmshop.logistics.LogisticsDtos.ProductStockView;
import com.osmshop.logistics.LogisticsDtos.RestockRequest;
import com.osmshop.logistics.LogisticsDtos.RestockResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.TransactionView;

public interface WarehouseService {
    PageResponse<ProductStockView> products(String search, boolean lowStockOnly, int page, int size);

    RestockResponse restock(long actorId, long productId, RestockRequest request);

    PageResponse<TransactionView> transactions(long productId, int page, int size);

    PageResponse<PackingOrderView> packingOrders(int page, int size);

    ShipmentDetailResponse pack(long actorId, long orderId, PackOrderRequest request);
}
