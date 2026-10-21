package com.osmshop.logistics;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistory, Long> {
    List<ShipmentStatusHistory> findByShipmentIdOrderByIdAsc(Long shipmentId);

    long countByShipmentIdAndNewStatus(Long shipmentId, ShipmentStatus newStatus);
}
