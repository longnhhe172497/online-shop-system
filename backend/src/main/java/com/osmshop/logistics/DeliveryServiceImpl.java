package com.osmshop.logistics;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import com.osmshop.logistics.LogisticsDtos.DeliveryDailyReport;
import com.osmshop.logistics.LogisticsDtos.DeliveryResultRow;
import com.osmshop.logistics.LogisticsDtos.ShipmentDetailResponse;
import com.osmshop.logistics.LogisticsDtos.ShipmentListItem;
import com.osmshop.logistics.LogisticsDtos.UpdateShipmentStatusRequest;
import com.osmshop.logistics.LogisticsQueries.OrderInfo;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
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
        List<ShipmentStatusHistory> existingAttempts = histories.findByShipmentIdOrderByIdAsc(shipmentId);

        switch (next) {
            case SHIPPING, IN_TRANSIT -> {
                long failures = histories.countByShipmentIdAndNewStatus(shipmentId, ShipmentStatus.DELIVERY_FAILED);
                if (failures >= maxDeliveryAttempts()) throw error(HttpStatus.CONFLICT, "MAX_ATTEMPTS_REACHED",
                        "Vận đơn đã đạt số lần giao tối đa.");
                if (s.getDeliveryStaffId() == null) s.setDeliveryStaffId(actorId);
                s.setAssignedAt(now);
                s.setDispatchedAt(now);
                s.setFailedReason(null);
                jdbc.update("""
                        UPDATE orders SET status='DELIVERING', updated_at=now()
                        WHERE id=? AND status IN ('READY_FOR_DELIVERY','ASSIGNED','DELIVERING','DELIVERY_FAILED')""",
                        s.getOrderId());
                if (note != null) {
                    jdbc.update("UPDATE orders SET internal_note=?, updated_at=now() WHERE id=?", note, s.getOrderId());
                }

                short nextAttemptNo = (short) Math.min(2, existingAttempts.size() + 1);
                histories.save(new ShipmentStatusHistory(shipmentId, nextAttemptNo, previous,
                        ShipmentStatus.SHIPPING, note, actorId));
            }
            case DELIVERED -> {
                boolean cod = s.getCodAmount().signum() > 0;
                if (cod && !Boolean.TRUE.equals(request.codCollected())) throw error(HttpStatus.BAD_REQUEST,
                        "COD_NOT_COLLECTED", "Cần xác nhận đã thu đủ tiền COD trước khi hoàn tất giao hàng.");
                s.setCodCollected(cod);
                s.setDeliveredAt(now);
                s.setFailedReason(null);
                jdbc.update("UPDATE orders SET status='DELIVERED', delivered_at=now(), updated_at=now() WHERE id=?",
                        s.getOrderId());
                if (note != null) {
                    jdbc.update("UPDATE orders SET internal_note=?, updated_at=now() WHERE id=?", note, s.getOrderId());
                }
                if (cod) {
                    jdbc.update("""
                            UPDATE payment_attempts SET status='PAID', paid_at=now(), recorded_by=?
                            WHERE order_id=? AND method='COD' AND status IN ('PENDING','UNPAID')""",
                            actorId, s.getOrderId());
                }
                saveOrUpdateAttempt(existingAttempts, shipmentId, previous, ShipmentStatus.DELIVERED, note, actorId, now);
            }
            case DELIVERY_FAILED -> {
                String reason = blankToNull(request.failedReason());
                if (reason == null) throw error(HttpStatus.BAD_REQUEST, "FAILED_REASON_REQUIRED",
                        "Cần nhập lý do giao thất bại.");
                s.setFailedReason(reason);
                if (note == null) note = reason;
                jdbc.update("UPDATE orders SET status='DELIVERY_FAILED', updated_at=now() WHERE id=?",
                        s.getOrderId());
                jdbc.update("UPDATE orders SET internal_note=?, updated_at=now() WHERE id=?", note, s.getOrderId());
                saveOrUpdateAttempt(existingAttempts, shipmentId, previous, ShipmentStatus.DELIVERY_FAILED, reason, actorId, now);
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "INVALID_TARGET_STATUS", "Trạng thái đích không hợp lệ.");
        }
        if (note != null) s.setDeliveryNotes(note);
        s.setStatus(next == ShipmentStatus.IN_TRANSIT ? ShipmentStatus.SHIPPING : next);
        shipments.save(s);
        return queries.detail(s);
    }

    private void saveOrUpdateAttempt(List<ShipmentStatusHistory> existingAttempts, long shipmentId,
                                     ShipmentStatus previous, ShipmentStatus target, String reasonOrNote,
                                     long actorId, OffsetDateTime now) {
        if (!existingAttempts.isEmpty()) {
            ShipmentStatusHistory current = existingAttempts.get(existingAttempts.size() - 1);
            if (current.getNewStatus() == ShipmentStatus.SHIPPING
                    || current.getNewStatus() == ShipmentStatus.READY_FOR_DISPATCH) {
                current.setNewStatus(target);
                current.setNote(reasonOrNote);
                current.setUpdatedBy(actorId);
                current.setFinishedAt(now);
                histories.save(current);
                return;
            }
        }
        short attemptNo = (short) Math.min(2, existingAttempts.size() + 1);
        histories.save(new ShipmentStatusHistory(shipmentId, attemptNo, previous, target, reasonOrNote, actorId));
    }

    @Transactional(readOnly = true)
    public DeliveryDailyReport dailyReport(long actorId, boolean admin) {
        String zone = queries.reportZone();
        Object[] totals = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE h.status='DELIVERED'),
                       count(*) FILTER (WHERE h.status='FAILED'),
                       COALESCE(sum(s.cod_amount) FILTER (WHERE h.status='DELIVERED' AND s.cod_collected_at IS NOT NULL), 0)
                FROM delivery_attempts h JOIN shipments s ON s.id=h.shipment_id
                WHERE h.status IN ('DELIVERED','FAILED')
                  AND (h.created_at AT TIME ZONE ?)::date = (now() AT TIME ZONE ?)::date
                  AND (? = true OR h.delivery_staff_id = ?)""",
                (rs, row) -> new Object[] {rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3)},
                zone, zone, admin, actorId);
        List<DeliveryResultRow> results = jdbc.query("""
                SELECT h.id, s.id, o.order_code, 'TRK-' || o.order_code, o.recipient_name, o.shipping_address, h.status,
                       CASE WHEN h.status='DELIVERED' AND s.cod_collected_at IS NOT NULL THEN s.cod_amount ELSE 0 END,
                       h.failure_reason, h.created_at
                FROM delivery_attempts h
                JOIN shipments s ON s.id=h.shipment_id JOIN orders o ON o.id=s.order_id
                WHERE h.status IN ('DELIVERED','FAILED')
                  AND (h.created_at AT TIME ZONE ?)::date = (now() AT TIME ZONE ?)::date
                  AND (? = true OR h.delivery_staff_id = ?)
                ORDER BY h.created_at DESC, h.id DESC LIMIT 500""",
                (rs, row) -> new DeliveryResultRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), ShipmentStatus.fromDbValue(rs.getString(7)),
                        rs.getBigDecimal(8), rs.getString(9), rs.getObject(10, OffsetDateTime.class)),
                zone, zone, admin, actorId);
        return new DeliveryDailyReport(queries.today(zone), (Integer) totals[0], (Integer) totals[1],
                (BigDecimal) totals[2], results);
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