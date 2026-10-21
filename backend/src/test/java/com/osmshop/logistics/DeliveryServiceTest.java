package com.osmshop.logistics;

import com.osmshop.auth.AuthApiException;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.UpdateShipmentStatusRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private ShipmentRepository shipments;
    @Mock private ShipmentStatusHistoryRepository histories;
    @Mock private LogisticsQueries queries;

    @InjectMocks private DeliveryServiceImpl service;

    // UTCID09: updateStatus_whenShipmentNotFound_throwNotFound
    @Test
    @DisplayName("UTCID09 - Cập nhật vận đơn không tồn tại ném 404")
    void updateStatus_whenShipmentNotFound_throwNotFound() {
        when(shipments.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(103L, false, 999L,
                new UpdateShipmentStatusRequest(ShipmentStatus.SHIPPING, null, null, null)))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Không tìm thấy vận đơn.");
    }

    // UTCID10: updateStatus_whenDeliveredWithoutCod_throwBadRequest
    @Test
    @DisplayName("UTCID10 - Giao đơn COD mà chưa xác nhận thu tiền ném 400 COD_NOT_COLLECTED")
    void updateStatus_whenDeliveredWithoutCod_throwBadRequest() {
        Shipment shipment = new Shipment();
        shipment.setStatus(ShipmentStatus.SHIPPING);
        shipment.setDeliveryStaffId(103L);
        shipment.setCodAmount(BigDecimal.valueOf(500000));

        when(shipments.findByIdForUpdate(301L)).thenReturn(Optional.of(shipment));

        UpdateShipmentStatusRequest req = new UpdateShipmentStatusRequest(ShipmentStatus.DELIVERED, null, null, false);

        assertThatThrownBy(() -> service.updateStatus(103L, false, 301L, req))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Cần xác nhận đã thu đủ tiền COD");
    }

    // UTCID11: updateStatus_whenFailedWithoutReason_throwBadRequest
    @Test
    @DisplayName("UTCID11 - Giao thất bại mà không nhập lý do ném 400 FAILED_REASON_REQUIRED")
    void updateStatus_whenFailedWithoutReason_throwBadRequest() {
        Shipment shipment = new Shipment();
        shipment.setStatus(ShipmentStatus.SHIPPING);
        shipment.setDeliveryStaffId(103L);

        when(shipments.findByIdForUpdate(301L)).thenReturn(Optional.of(shipment));

        UpdateShipmentStatusRequest req = new UpdateShipmentStatusRequest(ShipmentStatus.DELIVERY_FAILED, null, "   ", null);

        assertThatThrownBy(() -> service.updateStatus(103L, false, 301L, req))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Cần nhập lý do giao thất bại.");
    }

    // UTCID12: updateStatus_whenDeliveredWithCod_success
    @Test
    @DisplayName("UTCID12 - Giao đơn COD thành công khi đã xác nhận thu tiền")
    void updateStatus_whenDeliveredWithCod_success() {
        Shipment shipment = new Shipment();
        shipment.setStatus(ShipmentStatus.SHIPPING);
        shipment.setDeliveryStaffId(103L);
        shipment.setOrderId(201L);
        shipment.setCodAmount(BigDecimal.valueOf(500000));

        when(shipments.findByIdForUpdate(301L)).thenReturn(Optional.of(shipment));
        when(queries.detail(any())).thenReturn(mock(ShipmentDetailResponse.class));

        UpdateShipmentStatusRequest req = new UpdateShipmentStatusRequest(ShipmentStatus.DELIVERED, "Khách nhận đủ", null, true);

        ShipmentDetailResponse res = service.updateStatus(103L, false, 301L, req);

        assertThat(res).isNotNull();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.isCodCollected()).isTrue();
        verify(shipments).save(shipment);
        verify(histories).save(any());
    }
}