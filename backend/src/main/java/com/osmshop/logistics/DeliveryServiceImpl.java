package com.osmshop.logistics;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentListItem;
import com.osmshop.logistics.LogisticsDtos.UpdateShipmentStatusRequest;
import com.osmshop.logistics.LogisticsQueries.OrderInfo;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryServiceImpl implements DeliveryService {
    private static final int DEFAULT_MAX_ATTEMPTS = 2;

    private final JdbcTemplate jdbc;
    private final ShipmentRepository shipments;
    private final ShipmentStatusHistoryRepository histories;
    private final LogisticsQueries queries;

    public DeliveryServiceImpl(JdbcTemplate jdbc, ShipmentRepository shipments,
                               ShipmentStatusHistoryRepository histories, LogisticsQueries queries) {
        this.jdbc = jdbc;
        this.shipments = shipments;
        this.histories = histories;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShipmentListItem> list(long actorId, boolean admin, Scope scope, ShipmentStatus status,
                                               int page, int size) {
        validatePage(page, size);
        Specification<Shipment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(status != null ? cb.equal(root.get("status"), status)
                    : root.get("status").in(ShipmentStatus.DELIVERY_QUEUE));
            switch (scope) {
                case MINE -> predicates.add(cb.equal(root.get("deliveryStaffId"), actorId));
                case UNASSIGNED -> predicates.add(cb.isNull(root.get("deliveryStaffId")));
                case ALL -> {
                    if (!admin) predicates.add(cb.or(cb.isNull(root.get("deliveryStaffId")),
                            cb.equal(root.get("deliveryStaffId"), actorId)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Shipment> result = shipments.findAll(spec, PageRequest.of(page, size, Sort.by("id").ascending()));
        Map<Long, OrderInfo> orders = queries.orders(result.getContent().stream().map(Shipment::getOrderId).toList());
        List<ShipmentListItem> items = result.getContent().stream()
                .map(s -> queries.listItem(s, orders.get(s.getOrderId()))).toList();
        return new PageResponse<>(items, page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentDetailResponse get(long actorId, boolean admin, long shipmentId) {
        Shipment s = shipments.findById(shipmentId).orElseThrow(DeliveryServiceImpl::notFound);
        ensureAccess(s, actorId, admin);
        return queries.detail(s);
    }

    @Override
    @Transactional
    public ShipmentDetailResponse updateStatus(long actorId, boolean admin, long shipmentId,
                                               UpdateShipmentStatusRequest request) {
        Shipment s = shipments.findByIdForUpdate(shipmentId).orElseThrow(DeliveryServiceImpl::notFound);
        ShipmentStatus previous = s.getStatus();
        ShipmentStatus next = request.status();
        if (!previous.canMoveTo(next)) throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION",
                "Không thể chuyển vận đơn từ " + previous + " sang " + next + ".");
        ensureAccess(s, actorId, admin);

        OffsetDateTime now = OffsetDateTime.now();
        String note = blankToNull(request.note());
        switch (next) {
            case SHIPPING -> {
                if (previous == ShipmentStatus.DELIVERY_FAILED) {
                    long failures = histories.countByShipmentIdAndNewStatus(shipmentId, ShipmentStatus.DELIVERY_FAILED);
                    if (failures >= maxDeliveryAttempts()) throw error(HttpStatus.CONFLICT, "MAX_ATTEMPTS_REACHED",
                            "Vận đơn đã đạt số lần giao tối đa.");
                }
                if (s.getDeliveryStaffId() == null) s.setDeliveryStaffId(actorId);
                s.setDispatchedAt(now);
                s.setFailedReason(null);
                jdbc.update("UPDATE orders SET status='SHIPPING',updated_at=now() WHERE id=? AND status IN ('PACKED','SHIPPING')",
                        s.getOrderId());
            }
            case DELIVERED -> {
                boolean cod = s.getCodAmount().signum() > 0;
                if (cod && !Boolean.TRUE.equals(request.codCollected())) throw error(HttpStatus.BAD_REQUEST,
                        "COD_NOT_COLLECTED", "Cần xác nhận đã thu đủ tiền COD trước khi hoàn tất giao hàng.");
                s.setCodCollected(cod);
                s.setDeliveredAt(now);
                s.setFailedReason(null);
                jdbc.update("""
                        UPDATE orders SET status='DELIVERED',
                               payment_status = CASE WHEN payment_method='COD' THEN 'PAID' ELSE payment_status END,
                               updated_at=now() WHERE id=?""", s.getOrderId());
            }
            case DELIVERY_FAILED -> {
                String reason = blankToNull(request.failedReason());
                if (reason == null) throw error(HttpStatus.BAD_REQUEST, "FAILED_REASON_REQUIRED",
                        "Cần nhập lý do giao thất bại.");
                s.setFailedReason(reason);
                if (note == null) note = reason;
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "INVALID_TARGET_STATUS", "Trạng thái đích không hợp lệ.");
        }
        if (note != null) s.setDeliveryNotes(note);
        s.setStatus(next);
        shipments.save(s);
        histories.save(new ShipmentStatusHistory(shipmentId, previous, next, note, actorId));
        return queries.detail(s);
    }

    private int maxDeliveryAttempts() {
        List<String> values = jdbc.query("SELECT value_text FROM system_settings WHERE setting_key='max_delivery_attempts'",
                (rs, row) -> rs.getString(1));
        if (values.isEmpty()) return DEFAULT_MAX_ATTEMPTS;
        try {
            return Math.max(1, Integer.parseInt(values.get(0).trim()));
        } catch (NumberFormatException exception) {
            return DEFAULT_MAX_ATTEMPTS;
        }
    }

    private static void ensureAccess(Shipment s, long actorId, boolean admin) {
        Long owner = s.getDeliveryStaffId();
        if (!admin && owner != null && owner != actorId) throw error(HttpStatus.FORBIDDEN, "NOT_ASSIGNED",
                "Vận đơn đang được giao bởi nhân viên khác.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > 1_000_000)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Invalid page or size");
    }

    private static AuthApiException notFound() {
        return error(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "Không tìm thấy vận đơn.");
    }

    private static AuthApiException error(HttpStatus status, String code, String detail) {
        return new AuthApiException(status, code, detail);
    }
}
