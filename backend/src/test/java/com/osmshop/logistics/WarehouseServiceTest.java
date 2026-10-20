package com.osmshop.logistics;

import com.osmshop.auth.AuthApiException;
import com.osmshop.logistics.LogisticsDtos.PackOrderRequest;
import com.osmshop.logistics.LogisticsDtos.RestockRequest;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private ShipmentRepository shipments;
    @Mock private ShipmentStatusHistoryRepository histories;
    @Mock private InventoryTransactionRepository transactions;
    @Mock private LogisticsQueries queries;

    @InjectMocks private WarehouseServiceImpl service;

    // UTCID04: pack_whenOrderNotFound_throwNotFound
    @Test
    @DisplayName("UTCID04 - Đóng gói đơn không tồn tại ném 404 ORDER_NOT_FOUND")
    void pack_whenOrderNotFound_throwNotFound() {
        when(jdbc.query(contains("SELECT o.order_code"), any(RowMapper.class), eq(999L)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.pack(102L, 999L, new PackOrderRequest(null, null)))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng.");
    }

    // UTCID05: pack_whenStatusNotPackable_throwConflict
    @Test
    @DisplayName("UTCID05 - Đơn hàng sai trạng thái ném 409 ORDER_NOT_PACKABLE")
    void pack_whenStatusNotPackable_throwConflict() {
        when(jdbc.query(contains("SELECT o.order_code"), any(RowMapper.class), eq(201L)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getString(1)).thenReturn("ORD-01");
                    when(rs.getString(2)).thenReturn("COD");
                    when(rs.getString(3)).thenReturn("UNPAID");
                    when(rs.getBigDecimal(4)).thenReturn(BigDecimal.valueOf(250000));
                    when(rs.getString(5)).thenReturn("DELIVERED"); // Sai trạng thái (phải là CONFIRMED)
                    return List.of(mapper.mapRow(rs, 1));
                });

        assertThatThrownBy(() -> service.pack(102L, 201L, new PackOrderRequest(null, null)))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Chỉ đóng gói được đơn ở trạng thái CONFIRMED");
    }

    // UTCID06: pack_whenInsufficientStock_throwConflict
    @Test
    @DisplayName("UTCID06 - Tồn kho không đủ ném 409 INSUFFICIENT_STOCK")
    void pack_whenInsufficientStock_throwConflict() {
        when(jdbc.query(contains("SELECT o.order_code"), any(RowMapper.class), eq(201L)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getString(1)).thenReturn("ORD-01");
                    when(rs.getString(2)).thenReturn("COD");
                    when(rs.getString(3)).thenReturn("UNPAID");
                    when(rs.getBigDecimal(4)).thenReturn(BigDecimal.valueOf(250000));
                    when(rs.getString(5)).thenReturn("CONFIRMED");
                    return List.of(mapper.mapRow(rs, 1));
                });
        when(shipments.existsByOrderId(201L)).thenReturn(false);

        // Chi tiết đơn đặt 10 sản phẩm
        when(jdbc.query(contains("SELECT id, product_id, quantity"), any(RowMapper.class), eq(201L)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getLong(1)).thenReturn(1L);
                    when(rs.getLong(2)).thenReturn(101L);
                    when(rs.getInt(3)).thenReturn(10);
                    return List.of(mapper.mapRow(rs, 1));
                });

        // Tồn kho thực tế (stock_on_hand) chỉ còn 2
        when(jdbc.query(contains("SELECT sku, stock_on_hand"), any(RowMapper.class), eq(101L)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getString(1)).thenReturn("TS-01");
                    when(rs.getInt(2)).thenReturn(2); // Thiếu hàng
                    when(rs.getInt(3)).thenReturn(0);
                    return List.of(mapper.mapRow(rs, 1));
                });

        assertThatThrownBy(() -> service.pack(102L, 201L, new PackOrderRequest(null, null)))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Không đủ tồn kho");
    }

    // UTCID07: pack_whenValidRequest_success
    @Test
    @DisplayName("UTCID07 - Đóng gói hợp lệ thành công và tạo vận đơn")
    void pack_whenValidRequest_success() {
        when(jdbc.query(contains("SELECT o.order_code"), any(RowMapper.class), eq(201L)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getString(1)).thenReturn("ORD-01");
                    when(rs.getString(2)).thenReturn("COD");
                    when(rs.getString(3)).thenReturn("UNPAID");
                    when(rs.getBigDecimal(4)).thenReturn(BigDecimal.valueOf(250000));
                    when(rs.getString(5)).thenReturn("CONFIRMED");
                    return List.of(mapper.mapRow(rs, 1));
                });
        when(shipments.existsByOrderId(201L)).thenReturn(false);
        when(jdbc.query(contains("SELECT id, product_id, quantity"), any(RowMapper.class), eq(201L)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getLong(1)).thenReturn(1L);
                    when(rs.getLong(2)).thenReturn(101L);
                    when(rs.getInt(3)).thenReturn(1);
                    return List.of(mapper.mapRow(rs, 1));
                });
        when(jdbc.query(contains("SELECT sku, stock_on_hand"), any(RowMapper.class), eq(101L)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getString(1)).thenReturn("TS-01");
                    when(rs.getInt(2)).thenReturn(50);
                    when(rs.getInt(3)).thenReturn(2);
                    return List.of(mapper.mapRow(rs, 1));
                });

        Shipment mockShipment = new Shipment();
        mockShipment.setTrackingNumber("TRK-ORD-01");
        when(shipments.saveAndFlush(any())).thenReturn(mockShipment);
        when(queries.detail(any())).thenReturn(mock(ShipmentDetailResponse.class));

        ShipmentDetailResponse res = service.pack(102L, 201L, new PackOrderRequest("Express", "Gói cẩn thận"));

        assertThat(res).isNotNull();
        // Kiểm tra đúng trạng thái đơn READY_FOR_DELIVERY của V2 schema
        verify(jdbc).update(contains("UPDATE orders SET status='READY_FOR_DELIVERY'"), eq(201L));
        verify(transactions).save(any());
    }

    // UTCID08: restock_whenQuantityExceedsLimit_throwBadRequest
    @Test
    @DisplayName("UTCID08 - Nhập kho vượt giới hạn ném 400 QUANTITY_TOO_LARGE")
    void restock_whenQuantityExceedsLimit_throwBadRequest() {
        when(jdbc.query(contains("SELECT stock_on_hand FROM products"), any(RowMapper.class), eq(101L)))
                .thenReturn(List.of(Integer.MAX_VALUE));

        assertThatThrownBy(() -> service.restock(102L, 101L, new RestockRequest(10, null, null)))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Số lượng tồn kho vượt giới hạn cho phép.");
    }
}