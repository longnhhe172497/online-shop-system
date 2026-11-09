package com.osmshop.support;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import com.osmshop.support.AfterSalesDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterSalesService {
    private final JdbcTemplate jdbc;
    public AfterSalesService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public ReturnView createReturn(long customerId, long orderId, CreateReturnRequest request) {
        String type = request.requestType().trim().toUpperCase();
        if (!Set.of("REFUND_ONLY", "RETURN_AND_REFUND").contains(type))
            throw bad("INVALID_RETURN_TYPE", "Loại yêu cầu trả hàng không hợp lệ.");
        OrderRow order = jdbc.query("SELECT status,payment_method,delivered_at FROM orders WHERE id=? AND customer_id=? FOR UPDATE",
                (rs, row) -> new OrderRow(rs.getString(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class)), orderId, customerId)
                .stream().findFirst().orElseThrow(() -> notFound("Không tìm thấy đơn hàng."));
        if (!"DELIVERED".equals(order.status())) throw conflict("RETURN_ORDER_NOT_DELIVERED", "Chỉ có thể yêu cầu hậu mãi với đơn đã giao thành công.");
        List<String> returnWindowValues = jdbc.queryForList(
                "SELECT value_text FROM system_settings WHERE setting_key='return_window_days'", String.class);
        int returnWindow = returnWindowValues.isEmpty() ? 7 : Integer.parseInt(returnWindowValues.get(0));
        if (order.deliveredAt() == null || order.deliveredAt().plusDays(returnWindow).isBefore(OffsetDateTime.now()))
            throw conflict("RETURN_WINDOW_EXPIRED", "Đơn hàng đã quá thời hạn yêu cầu trả hàng.");
        Set<Long> unique = new HashSet<>();
        long requestId = jdbc.queryForObject("""
                INSERT INTO return_requests(order_id,customer_id,request_type,status,reason,evidence_url)
                VALUES (?,?,?,'REQUESTED',?,?) RETURNING id
                """, Long.class, orderId, customerId, type, request.reason().trim(), blankToNull(request.evidenceUrl()));
        for (ReturnItemRequest item : request.items()) {
            if (!unique.add(item.orderItemId())) throw bad("DUPLICATE_RETURN_ITEM", "Sản phẩm bị trùng trong yêu cầu.");
            ItemBase base = jdbc.query("""
                    SELECT oi.quantity,oi.line_total,o.subtotal,o.discount_amount
                    FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE oi.id=? AND oi.order_id=?
                    """, (rs, row) -> new ItemBase(rs.getInt(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4)),
                    item.orderItemId(), orderId).stream().findFirst()
                    .orElseThrow(() -> bad("INVALID_ORDER_ITEM", "Sản phẩm không thuộc đơn hàng này."));
            Integer alreadyRequested = jdbc.queryForObject("""
                    SELECT COALESCE(sum(CASE
                        WHEN rr.status IN ('REFUNDED','PARTIALLY_REFUNDED') AND rr.request_type='RETURN_AND_REFUND'
                            THEN COALESCE(i.received_quantity,0)
                        ELSE ri.quantity END),0)::integer
                    FROM return_request_items ri JOIN return_requests rr ON rr.id=ri.return_request_id
                    LEFT JOIN return_inspections i ON i.return_request_item_id=ri.id
                    WHERE ri.order_item_id=? AND rr.status NOT IN ('REJECTED','CLOSED')
                    """, Integer.class, item.orderItemId());
            if (item.quantity() > base.quantity() - (alreadyRequested == null ? 0 : alreadyRequested))
                throw conflict("RETURN_QUANTITY_EXCEEDED", "Số lượng yêu cầu vượt quá số lượng còn có thể trả.");
            BigDecimal discountedSubtotal = base.subtotal().subtract(base.orderDiscount());
            BigDecimal itemNet = base.subtotal().signum() == 0 ? BigDecimal.ZERO
                    : base.lineTotal().multiply(discountedSubtotal).divide(base.subtotal(), 2, RoundingMode.HALF_UP);
            BigDecimal amount = itemNet.multiply(BigDecimal.valueOf(item.quantity()))
                    .divide(BigDecimal.valueOf(base.quantity()), 2, RoundingMode.HALF_UP);
            jdbc.update("INSERT INTO return_request_items(return_request_id,order_id,order_item_id,quantity,refund_amount) VALUES (?,?,?,?,?)",
                    requestId, orderId, item.orderItemId(), item.quantity(), amount);
        }
        audit(customerId, "RETURN_REQUEST_CREATED", "RETURN_REQUEST", requestId, "{}");
        return returnDetail(requestId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnView> returns(String status, int page, int size) {
        int p = Math.max(0, page), s = clamp(size);
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<Long> ids = jdbc.queryForList("SELECT id FROM return_requests WHERE (CAST(? AS varchar) IS NULL OR status=?) ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                Long.class, filter, filter, s, p * s);
        List<ReturnView> items = ids.stream().map(this::returnDetail).toList();
        Long total = jdbc.queryForObject("SELECT count(*) FROM return_requests WHERE (CAST(? AS varchar) IS NULL OR status=?)", Long.class, filter, filter);
        return new PageResponse<>(items, p, s, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<ReturnView> customerReturns(long customerId) {
        return jdbc.queryForList("SELECT id FROM return_requests WHERE customer_id=? ORDER BY created_at DESC", Long.class, customerId)
                .stream().map(this::returnDetail).toList();
    }

    @Transactional
    public ReturnView decideReturn(long actorId, long requestId, ReturnDecisionRequest request) {
        String note = blankToNull(request.note());
        if (!Boolean.TRUE.equals(request.approved()) && note == null)
            throw bad("REJECT_NOTE_REQUIRED", "Vui lòng nhập ghi chú lý do từ chối.");
        ReturnHead head = lockReturn(requestId);
        if (!Set.of("REQUESTED", "UNDER_REVIEW", "INSPECTED").contains(head.status()))
            throw conflict("RETURN_ALREADY_DECIDED", "Yêu cầu này không còn ở trạng thái chờ quyết định.");
        if (!Boolean.TRUE.equals(request.approved())) {
            jdbc.update("UPDATE return_requests SET status='REJECTED',decision_note=?,decided_by=?,decided_at=now(),updated_at=now() WHERE id=?",
                    note, actorId, requestId);
            audit(actorId, "RETURN_REJECTED", "RETURN_REQUEST", requestId, "{}");
            return returnDetail(requestId);
        }
        if ("RETURN_AND_REFUND".equals(head.type()) && !"INSPECTED".equals(head.status())) {
            if ("REQUESTED".equals(head.status()) || "UNDER_REVIEW".equals(head.status())) {
                jdbc.update("UPDATE return_requests SET status='RETURN_AUTHORIZED',decision_note=?,decided_by=?,decided_at=now(),updated_at=now() WHERE id=?",
                        note, actorId, requestId);
                audit(actorId, "RETURN_AUTHORIZED", "RETURN_REQUEST", requestId, "{}");
                return returnDetail(requestId);
            }
            throw conflict("RETURN_INSPECTION_REQUIRED", "Cần kiểm tra hàng trả trước khi duyệt hoàn tiền.");
        }
        BigDecimal amount = jdbc.queryForObject("""
                SELECT COALESCE(sum(CASE WHEN rr.request_type='RETURN_AND_REFUND'
                    THEN ri.refund_amount * COALESCE(i.received_quantity,0)::numeric / ri.quantity
                    ELSE ri.refund_amount END),0)
                FROM return_request_items ri JOIN return_requests rr ON rr.id=ri.return_request_id
                LEFT JOIN return_inspections i ON i.return_request_item_id=ri.id WHERE rr.id=?
                AND (rr.request_type='REFUND_ONLY' OR i.result='ELIGIBLE')
                """, BigDecimal.class, requestId);
        if (amount == null || amount.signum() <= 0) throw conflict("NO_REFUNDABLE_ITEMS", "Không có sản phẩm đủ điều kiện hoàn tiền.");
        String method = "COD".equals(head.paymentMethod()) ? "MANUAL_COD" : "SIMULATED_QR";
        jdbc.update("""
                INSERT INTO refunds(return_request_id,order_id,reason,method,status,amount,idempotency_key,approved_by)
                VALUES (?,?, 'CUSTOMER_RETURN',?,'PENDING',?,?,?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, requestId, head.orderId(), method, amount, "RETURN-REQ-" + requestId, actorId);
        jdbc.update("UPDATE return_requests SET status='REFUND_PENDING',decision_note=?,decided_by=?,decided_at=now(),updated_at=now() WHERE id=?",
                note, actorId, requestId);
        audit(actorId, "RETURN_APPROVED", "RETURN_REQUEST", requestId, "{}");
        return returnDetail(requestId);
    }

    @Transactional
    public ReturnView inspectReturn(long actorId, long requestId, InspectionRequest request) {
        ReturnHead head = lockReturn(requestId);
        if (!"RETURN_AUTHORIZED".equals(head.status())) throw conflict("RETURN_NOT_AUTHORIZED", "Yêu cầu chưa được quản lý cho phép nhận hàng trả.");
        List<Long> expected = jdbc.queryForList("SELECT id FROM return_request_items WHERE return_request_id=? ORDER BY id", Long.class, requestId);
        Set<Long> supplied = new HashSet<>();
        if (request.items().size() != expected.size()) throw bad("INSPECTION_ITEMS_MISMATCH", "Cần kiểm tra tất cả sản phẩm trong yêu cầu.");
        for (ItemInspection item : request.items()) {
            if (!supplied.add(item.returnRequestItemId()) || !expected.contains(item.returnRequestItemId()))
                throw bad("INVALID_INSPECTION_ITEM", "Sản phẩm kiểm tra không thuộc yêu cầu hoặc bị trùng.");
            int requested = jdbc.queryForObject("SELECT quantity FROM return_request_items WHERE id=? AND return_request_id=?",
                    Integer.class, item.returnRequestItemId(), requestId);
            if (item.receivedQuantity() > requested || item.restockQuantity() > item.receivedQuantity())
                throw bad("INVALID_INSPECTION_QUANTITY", "Số lượng nhận/nhập kho không hợp lệ.");
            String result = item.receivedQuantity() > 0 ? "ELIGIBLE" : "NOT_ELIGIBLE";
            jdbc.update("""
                    INSERT INTO return_inspections(return_request_item_id,inspected_by,received_quantity,condition_note,restock_quantity,result)
                    VALUES (?,?,?,?,?,?)
                    """, item.returnRequestItemId(), actorId, item.receivedQuantity(), item.conditionNote().trim(), item.restockQuantity(), result);
            if (item.restockQuantity() > 0) {
                Long productId = jdbc.queryForObject("SELECT product_id FROM order_items oi JOIN return_request_items ri ON ri.order_item_id=oi.id WHERE ri.id=?",
                        Long.class, item.returnRequestItemId());
                Integer stock = jdbc.queryForObject("UPDATE products SET stock_on_hand=stock_on_hand+?,updated_at=now() WHERE id=? RETURNING stock_on_hand",
                        (rs, row) -> rs.getInt(1), item.restockQuantity(), productId);
                jdbc.update("""
                        INSERT INTO inventory_movements(product_id,movement_type,quantity_delta,stock_after,order_item_id,reason,performed_by)
                        SELECT ?, 'RETURN_RESTOCK', ?, ?, ri.order_item_id, 'Restock from approved return inspection', ?
                        FROM return_request_items ri WHERE ri.id=?
                        """, productId, item.restockQuantity(), stock, actorId, item.returnRequestItemId());
            }
        }
        jdbc.update("UPDATE return_requests SET status='INSPECTED',updated_at=now() WHERE id=?", requestId);
        audit(actorId, "RETURN_INSPECTED", "RETURN_REQUEST", requestId, "{}");
        return returnDetail(requestId);
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundView> refunds(String status, int page, int size) {
        int p = Math.max(0, page), s = clamp(size);
        String f = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<RefundView> rows = jdbc.query("""
                SELECT r.id,r.order_id,r.return_request_id,o.order_code,r.reason,r.method,r.status,r.amount,r.created_at,r.completed_at
                FROM refunds r JOIN orders o ON o.id=r.order_id WHERE (CAST(? AS varchar) IS NULL OR r.status=?)
                ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?
                """, AfterSalesService::mapRefund, f, f, s, p*s);
        Long total = jdbc.queryForObject("SELECT count(*) FROM refunds WHERE (CAST(? AS varchar) IS NULL OR status=?)", Long.class, f, f);
        return new PageResponse<>(rows,p,s,total == null ? 0 : total);
    }

    @Transactional
    public RefundView completeRefund(long actorId, long refundId) {
        Integer changed = jdbc.update("UPDATE refunds SET status='COMPLETED',completed_by=?,completed_at=now() WHERE id=? AND status='PENDING'",
                actorId, refundId);
        if (changed == 0) throw conflict("REFUND_NOT_PENDING", "Khoản hoàn tiền không tồn tại hoặc không còn chờ xử lý.");
        jdbc.update("""
                UPDATE return_requests SET status='REFUNDED',updated_at=now()
                WHERE id=(SELECT return_request_id FROM refunds WHERE id=?) AND status='REFUND_PENDING'
                """, refundId);
        audit(actorId, "REFUND_COMPLETED", "REFUND", refundId, "{\"mode\":\"manual_confirmation\"}");
        return jdbc.queryForObject("""
                SELECT r.id,r.order_id,r.return_request_id,o.order_code,r.reason,r.method,r.status,r.amount,r.created_at,r.completed_at
                FROM refunds r JOIN orders o ON o.id=r.order_id WHERE r.id=?
                """, AfterSalesService::mapRefund, refundId);
    }

    @Transactional(readOnly = true)
    public List<FailedDeliveryOrderView> failedDeliveryRefundCandidates() {
        return jdbc.query("""
                SELECT o.id,o.order_code,u.full_name,o.total_amount,o.updated_at
                FROM orders o JOIN users u ON u.id=o.customer_id
                WHERE o.status='RETURNED_TO_WAREHOUSE' AND o.payment_method='SIMULATED_QR'
                  AND EXISTS (SELECT 1 FROM payment_attempts p WHERE p.order_id=o.id AND p.method='SIMULATED_QR' AND p.status='PAID')
                  AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.order_id=o.id AND r.status<>'FAILED')
                ORDER BY o.updated_at DESC,o.id DESC LIMIT 100
                """, (rs, row) -> new FailedDeliveryOrderView(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4), rs.getObject(5, OffsetDateTime.class)));
    }

    @Transactional
    public RefundView createFailedDeliveryRefund(long actorId, long orderId) {
        FailedOrderRow order = jdbc.query("SELECT status,payment_method,total_amount FROM orders WHERE id=? FOR UPDATE",
                (rs, row) -> new FailedOrderRow(rs.getString(1), rs.getString(2), rs.getBigDecimal(3)), orderId)
                .stream().findFirst().orElseThrow(() -> notFound("Không tìm thấy đơn hàng."));
        if (!"RETURNED_TO_WAREHOUSE".equals(order.status()))
            throw conflict("ORDER_NOT_RETURNED", "Chỉ tạo hoàn tiền cho đơn đã hoàn về kho sau khi giao thất bại.");
        if (!"SIMULATED_QR".equals(order.paymentMethod()))
            throw conflict("ORDER_NOT_PREPAID", "Đơn COD chưa thu tiền nên không cần hoàn tiền.");
        Integer paid = jdbc.queryForObject("SELECT count(*) FROM payment_attempts WHERE order_id=? AND method='SIMULATED_QR' AND status='PAID'",
                Integer.class, orderId);
        if (paid == null || paid == 0) throw conflict("ORDER_NOT_PAID", "Đơn hàng chưa được thanh toán.");
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM refunds WHERE order_id=? AND status<>'FAILED'", Integer.class, orderId);
        if (existing != null && existing > 0) throw conflict("REFUND_ALREADY_EXISTS", "Đơn hàng này đã có khoản hoàn tiền.");
        if (order.totalAmount() == null || order.totalAmount().signum() <= 0)
            throw conflict("NO_REFUNDABLE_AMOUNT", "Đơn hàng không có số tiền cần hoàn.");
        Long refundId = jdbc.queryForObject("""
                INSERT INTO refunds(order_id,reason,method,status,amount,idempotency_key,approved_by)
                VALUES (?,'FAILED_PREPAID_DELIVERY','SIMULATED_QR','PENDING',?,?,?) RETURNING id
                """, Long.class, orderId, order.totalAmount(), "FAILED-DELIVERY-" + orderId, actorId);
        audit(actorId, "REFUND_CREATED", "REFUND", refundId, "{\"reason\":\"FAILED_PREPAID_DELIVERY\"}");
        return jdbc.queryForObject("""
                SELECT r.id,r.order_id,r.return_request_id,o.order_code,r.reason,r.method,r.status,r.amount,r.created_at,r.completed_at
                FROM refunds r JOIN orders o ON o.id=r.order_id WHERE r.id=?
                """, AfterSalesService::mapRefund, refundId);
    }

    @Transactional(readOnly = true)
    public List<TicketView> customerTickets(long customerId) {
        return jdbc.queryForList("SELECT id FROM support_tickets WHERE customer_id=? ORDER BY updated_at DESC", Long.class, customerId)
                .stream().map(id -> ticketDetail(id, customerId, false)).toList();
    }

    @Transactional
    public TicketView createTicket(long customerId, CreateTicketRequest request) {
        if (request.orderId() != null && jdbc.queryForObject("SELECT count(*) FROM orders WHERE id=? AND customer_id=?", Integer.class,
                request.orderId(), customerId) == 0) throw notFound("Không tìm thấy đơn hàng liên kết.");
        long id = jdbc.queryForObject("INSERT INTO support_tickets(customer_id,order_id,subject) VALUES (?,?,?) RETURNING id",
                Long.class, customerId, request.orderId(), request.subject().trim());
        jdbc.update("INSERT INTO ticket_messages(ticket_id,sender_id,message) VALUES (?,?,?)", id, customerId, request.message().trim());
        audit(customerId, "SUPPORT_TICKET_CREATED", "SUPPORT_TICKET", id, "{}");
        return ticketDetail(id, customerId, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketView> ticketQueue(String status, int page, int size) {
        int p=Math.max(0,page), s=clamp(size); String f=status==null||status.isBlank()?null:status.trim().toUpperCase();
        List<Long> ids=jdbc.queryForList("SELECT id FROM support_tickets WHERE (CAST(? AS varchar) IS NULL OR status=?) ORDER BY updated_at DESC LIMIT ? OFFSET ?",Long.class,f,f,s,p*s);
        List<TicketView> rows=ids.stream().map(id->ticketDetail(id,0,true)).toList();
        Long total=jdbc.queryForObject("SELECT count(*) FROM support_tickets WHERE (CAST(? AS varchar) IS NULL OR status=?)",Long.class,f,f);
        return new PageResponse<>(rows,p,s,total==null?0:total);
    }

    @Transactional
    public TicketView customerReplyTicket(long customerId, long ticketId, String message) {
        TicketHead head=jdbc.query("SELECT t.id,t.customer_id,u.full_name,t.order_id,o.order_code,t.subject,t.status,t.assigned_staff_id,t.created_at,t.updated_at FROM support_tickets t JOIN users u ON u.id=t.customer_id LEFT JOIN orders o ON o.id=t.order_id WHERE t.id=? AND t.customer_id=? FOR UPDATE OF t",
                (rs,row)->new TicketHead(rs.getLong(1),rs.getLong(2),rs.getString(3),(Long)rs.getObject(4),rs.getString(5),rs.getString(6),rs.getString(7),(Long)rs.getObject(8),rs.getObject(9,OffsetDateTime.class),rs.getObject(10,OffsetDateTime.class)),ticketId,customerId)
                .stream().findFirst().orElseThrow(()->notFound("Không tìm thấy ticket."));
        if("CLOSED".equals(head.status())) throw conflict("TICKET_CLOSED","Ticket đã đóng, không thể gửi thêm tin nhắn.");
        jdbc.update("INSERT INTO ticket_messages(ticket_id,sender_id,message) VALUES (?,?,?)",ticketId,customerId,message.trim());
        jdbc.update("UPDATE support_tickets SET updated_at=now() WHERE id=?",ticketId);
        return ticketDetail(ticketId,customerId,false);
    }

    @Transactional
    public TicketView replyTicket(long staffId, long ticketId, String message) {
        String status=jdbc.query("SELECT status FROM support_tickets WHERE id=? FOR UPDATE",
                (rs,row)->rs.getString(1),ticketId).stream().findFirst().orElseThrow(()->notFound("Không tìm thấy ticket."));
        if("CLOSED".equals(status)) throw conflict("TICKET_CLOSED","Ticket đã đóng, không thể gửi thêm tin nhắn.");
        jdbc.update("INSERT INTO ticket_messages(ticket_id,sender_id,message) VALUES (?,?,?)",ticketId,staffId,message.trim());
        jdbc.update("UPDATE support_tickets SET assigned_staff_id=COALESCE(assigned_staff_id,?),status=CASE WHEN status='OPEN' THEN 'IN_PROGRESS' ELSE status END,updated_at=now() WHERE id=?",staffId,ticketId);
        audit(staffId,"SUPPORT_TICKET_REPLIED","SUPPORT_TICKET",ticketId,"{}");
        return ticketDetail(ticketId,0,true);
    }

    @Transactional
    public TicketView updateTicketStatus(long staffId,long ticketId,String status) {
        String current=jdbc.query("SELECT status FROM support_tickets WHERE id=? FOR UPDATE",
                (rs,row)->rs.getString(1),ticketId).stream().findFirst().orElseThrow(()->notFound("Không tìm thấy ticket."));
        if("CLOSED".equals(current)) throw conflict("TICKET_CLOSED","Ticket đã đóng.");
        jdbc.update("UPDATE support_tickets SET status=?,assigned_staff_id=COALESCE(assigned_staff_id,?),updated_at=now() WHERE id=?",status,staffId,ticketId);
        audit(staffId,"SUPPORT_TICKET_STATUS_CHANGED","SUPPORT_TICKET",ticketId,"{\"status\":\""+status+"\"}");
        return ticketDetail(ticketId,0,true);
    }

    private ReturnView returnDetail(long id) {
        ReturnView head=jdbc.queryForObject("""
                SELECT rr.id,rr.order_id,o.order_code,rr.customer_id,u.full_name,rr.request_type,rr.status,rr.reason,rr.evidence_url,rr.decision_note,rr.created_at
                FROM return_requests rr JOIN orders o ON o.id=rr.order_id JOIN users u ON u.id=rr.customer_id WHERE rr.id=?
                """,(rs,row)->new ReturnView(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getObject(11,OffsetDateTime.class),List.of()),id);
        List<ReturnItemView> items=jdbc.query("""
                SELECT ri.id,ri.order_item_id,oi.name_snapshot,oi.sku_snapshot,ri.quantity,ri.refund_amount,
                       i.received_quantity,i.restock_quantity,i.result,i.condition_note
                FROM return_request_items ri JOIN order_items oi ON oi.id=ri.order_item_id
                LEFT JOIN return_inspections i ON i.return_request_item_id=ri.id
                WHERE ri.return_request_id=? ORDER BY ri.id
                """,(rs,row)->new ReturnItemView(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getBigDecimal(6),
                        (Integer)rs.getObject(7),(Integer)rs.getObject(8),rs.getString(9),rs.getString(10)),id);
        return new ReturnView(head.id(),head.orderId(),head.orderCode(),head.customerId(),head.customerName(),head.requestType(),head.status(),head.reason(),head.evidenceUrl(),head.decisionNote(),head.createdAt(),items);
    }

    private TicketView ticketDetail(long id,long customerId,boolean staff) {
        TicketHead h=jdbc.query("""
                SELECT t.id,t.customer_id,u.full_name,t.order_id,o.order_code,t.subject,t.status,t.assigned_staff_id,t.created_at,t.updated_at
                FROM support_tickets t JOIN users u ON u.id=t.customer_id LEFT JOIN orders o ON o.id=t.order_id WHERE t.id=?
                """,(rs,row)->new TicketHead(rs.getLong(1),rs.getLong(2),rs.getString(3),(Long)rs.getObject(4),rs.getString(5),rs.getString(6),rs.getString(7),(Long)rs.getObject(8),rs.getObject(9,OffsetDateTime.class),rs.getObject(10,OffsetDateTime.class)),id)
                .stream().findFirst().orElseThrow(()->notFound("Không tìm thấy ticket."));
        if(!staff && h.customerId()!=customerId) throw notFound("Không tìm thấy ticket.");
        List<TicketMessageView> messages=jdbc.query("SELECT m.id,m.sender_id,u.full_name,m.message,m.created_at FROM ticket_messages m JOIN users u ON u.id=m.sender_id WHERE m.ticket_id=? ORDER BY m.created_at,m.id",
                (rs,row)->new TicketMessageView(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getObject(5,OffsetDateTime.class)),id);
        return new TicketView(h.id(),h.customerId(),h.customerName(),h.orderId(),h.orderCode(),h.subject(),h.status(),h.assignedStaffId(),h.createdAt(),h.updatedAt(),messages);
    }

    private ReturnHead lockReturn(long id) {
        return jdbc.query("SELECT rr.order_id,rr.request_type,rr.status,o.payment_method FROM return_requests rr JOIN orders o ON o.id=rr.order_id WHERE rr.id=? FOR UPDATE OF rr",
                (rs,row)->new ReturnHead(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),id)
                .stream().findFirst().orElseThrow(()->notFound("Không tìm thấy yêu cầu trả hàng."));
    }
    private void audit(long actor,String action,String type,long id,String details){jdbc.update("INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details) VALUES (?,?,?,?,?::jsonb)",actor,action,type,Long.toString(id),details);}
    private static RefundView mapRefund(ResultSet rs,int row)throws SQLException{return new RefundView(rs.getLong(1),rs.getLong(2),(Long)rs.getObject(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBigDecimal(8),rs.getObject(9,OffsetDateTime.class),rs.getObject(10,OffsetDateTime.class));}
    private static String blankToNull(String s){return s==null||s.isBlank()?null:s.trim();}
    private static int clamp(int s){return Math.max(1,Math.min(100,s));}
    private static AuthApiException bad(String c,String m){return new AuthApiException(HttpStatus.BAD_REQUEST,c,m);}
    private static AuthApiException notFound(String m){return new AuthApiException(HttpStatus.NOT_FOUND,"NOT_FOUND",m);}
    private static AuthApiException conflict(String c,String m){return new AuthApiException(HttpStatus.CONFLICT,c,m);}
    private record OrderRow(String status,String paymentMethod,OffsetDateTime deliveredAt){}
    private record ItemBase(int quantity,BigDecimal lineTotal,BigDecimal subtotal,BigDecimal orderDiscount){}
    private record FailedOrderRow(String status,String paymentMethod,BigDecimal totalAmount){}
    private record ReturnHead(long orderId,String type,String status,String paymentMethod){}
    private record TicketHead(long id,long customerId,String customerName,Long orderId,String orderCode,String subject,String status,Long assignedStaffId,OffsetDateTime createdAt,OffsetDateTime updatedAt){}
}
