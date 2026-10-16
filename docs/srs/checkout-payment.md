# Phân hệ 3 — Checkout & Thanh toán (đặc tả nghiệp vụ)

Phạm vi của nhóm việc 3 trong [`TEAM_START.md`](../TEAM_START.md): voucher, đặt hàng, giữ/trả tồn, thanh toán QR giả lập, COD, thử lại QR, lịch sử/chi tiết/hủy đơn và email xác nhận. Route và mã lỗi chi tiết nằm trong [`API_CONTRACT.md`](../API_CONTRACT.md) và [`openapi.yaml`](../../openapi.yaml).

> Thanh toán QR và hoàn tiền là **mô phỏng**. Giao diện và API luôn ghi rõ: *"Thanh toán mô phỏng — không có tiền thật được chuyển"*.

## 1. Tác nhân

| Tác nhân | Việc làm trong phân hệ |
|---|---|
| Khách hàng (Customer) | Xem trước đơn, áp voucher, đặt hàng, thanh toán QR giả lập, thử lại QR, xem lịch sử/chi tiết, hủy đơn |
| Quản lý (Manager, cần quyền `MANAGER_VOUCHER_MANAGE`) | Tạo, sửa, xóa voucher |
| Hệ thống | Hết hạn lượt QR, trả tồn và voucher, tự hủy đơn khi hết lượt QR, gửi email |

Admin **không** tự có quyền quản lý voucher (Admin không kế thừa quyền nghiệp vụ).

## 2. Ca sử dụng

| Mã | Ca sử dụng | API |
|---|---|---|
| UC-C1 | Xem trước đơn và áp voucher | `POST /orders/preview` |
| UC-C2 | Đặt hàng (QR hoặc COD) | `POST /orders` |
| UC-C3 | Thanh toán QR giả lập (thành công/thất bại) | `POST /payment-attempts/{id}/simulate` |
| UC-C4 | Tạo lại mã QR | `POST /orders/{id}/payment-attempts` |
| UC-C5 | Xem lịch sử và chi tiết đơn | `GET /orders`, `GET /orders/{id}` |
| UC-C6 | Hủy đơn | `POST /orders/{id}/cancel` |
| UC-C7 | Quản lý voucher | `GET/POST /manager/vouchers`, `PATCH/DELETE /manager/vouchers/{id}` |
| UC-C8 | Hết hạn QR tự động | tác vụ nền mỗi 30 giây |
| UC-C9 | Email xác nhận | gửi sau khi giao dịch đã lưu |

## 3. Luồng chính

### UC-C2 Đặt hàng
1. Khách mở trang Thanh toán; hệ thống đọc giỏ `ACTIVE` của khách (giỏ do phân hệ 2 quản lý).
2. Khách chọn địa chỉ đã lưu, phương thức thanh toán, nhập voucher (tùy chọn) và bấm **Đặt hàng**.
3. Hệ thống khóa giỏ, khóa các dòng sản phẩm theo thứ tự id, kiểm tra lại: sản phẩm và danh mục còn `ACTIVE`, số lượng ≤ tồn khả dụng (`stock_on_hand − stock_reserved`).
4. Kiểm tra voucher (BR-V1…V4), tính tiền (BR-P1…P3).
5. Tạo đơn với **bản chụp** địa chỉ, tên, SKU, giá; tạo lượt thanh toán số 1; giữ tồn; giữ voucher; chuyển giỏ sang `CONVERTED`; ghi audit; gửi email.

| Phương thức | Trạng thái đơn | Lượt thanh toán | Giữ tồn |
|---|---|---|---|
| `SIMULATED_QR` | `AWAITING_PAYMENT` | `PENDING`, hết hạn sau `qr_expiry_minutes` | gắn với lượt QR, cùng hạn |
| `COD` | `PENDING_CONFIRMATION` | `UNPAID` | đến khi đóng gói hoặc hủy |

### UC-C3 Thanh toán QR giả lập
- **Thành công:** lượt → `PAID`; đơn → `CONFIRMED`; voucher → `CONSUMED`; tồn vẫn được giữ đến khi kho đóng gói.
- **Thất bại:** lượt → `FAILED`; trả tồn và voucher của lượt đó; đơn vẫn `AWAITING_PAYMENT` để thử lại.
- Lượt đã quá hạn → `EXPIRED` và báo lỗi `PAYMENT_ATTEMPT_EXPIRED`.

### UC-C4 Tạo lại mã QR
Chỉ khi đơn QR đang `AWAITING_PAYMENT`, không có lượt `PENDING` còn hạn và chưa dùng hết `max_qr_attempts` (mặc định 3). Hệ thống **kiểm tra lại từ đầu**: sản phẩm còn bán, giá không đổi so với bản chụp, đủ tồn, voucher còn hiệu lực; rồi tạo lượt mới, giữ tồn mới và giữ lại voucher.

### UC-C6 Hủy đơn
Cho phép khi đơn ở `AWAITING_PAYMENT`, `PENDING_CONFIRMATION` hoặc `CONFIRMED` (trước khi đóng gói). Hệ thống trả tồn, trả voucher, hủy lượt thanh toán chưa xong. Nếu đơn QR **đã thanh toán**, tạo bản ghi hoàn tiền mô phỏng `PENDING` (lý do `CANCELLED_PAID_ORDER`), phân hệ 5 hoàn tất.

### UC-C8 Hết hạn QR
Tác vụ nền tìm lượt `PENDING` đã quá `expires_at`: lượt → `EXPIRED`, trả tồn và voucher. Nếu đó là lượt cuối cùng được phép, đơn tự chuyển `CANCELLED` (ghi chú "Hết số lần thanh toán QR").

## 4. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| BR-S1 | Đặt hàng chỉ **giữ** tồn (`stock_reserved`). `stock_on_hand` chỉ giảm **một lần** khi kho đóng gói (phân hệ 4). |
| BR-S2 | Mỗi dòng sản phẩm của đơn có tối đa một lượt giữ `ACTIVE`. Mọi đường trả tồn (thất bại, hết hạn, hủy) đều giảm `stock_reserved` đúng số đã giữ. |
| BR-S3 | Khóa dòng sản phẩm khi đặt hàng/thử lại để hai khách không mua vượt tồn. |
| BR-P1 | `total = subtotal − discount + shipping_fee`; phí ship phẳng theo thiết lập `shipping_fee` (mặc định 30.000 đ). |
| BR-P2 | Giảm giá được chia cho từng dòng theo tỷ lệ, làm tròn đến **đồng** (không có số lẻ), tổng các dòng bằng đúng giảm giá đơn. |
| BR-P3 | Giá trong đơn là bản chụp; thử lại QR bị từ chối (`PRICE_CHANGED`) nếu giá hiện tại khác. |
| BR-V1 | Voucher phải tồn tại (không phân biệt hoa thường), `ACTIVE` và trong thời gian hiệu lực. |
| BR-V2 | `subtotal ≥ min_order_amount`. |
| BR-V3 | Số lượt `RESERVED + CONSUMED` < `usage_limit`. |
| BR-V4 | `PERCENT`: giảm `subtotal × value%`, làm tròn xuống đồng, không vượt `max_discount`; `FIXED`: giảm `value`. Không vượt subtotal. |
| BR-V5 | Voucher được giữ (`RESERVED`) khi đặt; `CONSUMED` khi QR thanh toán thành công; `RELEASED` khi thất bại/hết hạn/hủy. Với COD, phân hệ 4 chuyển `CONSUMED` khi giao thành công. |
| BR-Q1 | Tối đa `max_qr_attempts` lượt QR mỗi đơn; tại một thời điểm chỉ có một lượt `PENDING`. |
| BR-O1 | Khách chỉ thấy đơn của mình; đơn của người khác trả `404`. |
| BR-O2 | Bấm đặt hàng hai lần không tạo hai đơn: lần sau không còn giỏ `ACTIVE` (`CART_EMPTY`). |
| BR-M1 | Mã voucher 3–60 ký tự `A-Z 0-9 _ -`, lưu chữ hoa, không trùng. Không xóa voucher đã từng dùng (chuyển `INACTIVE`). Không hạ `usage_limit` dưới số lượt đã dùng. |

## 5. Trạng thái

```
Đơn QR : AWAITING_PAYMENT ──thanh toán OK──► CONFIRMED ──(phân hệ 4)──► READY_FOR_DELIVERY …
          │  └─thất bại/hết hạn (còn lượt)──► AWAITING_PAYMENT (tạo lại QR)
          ├─hết lượt QR──► CANCELLED
          └─khách hủy──► CANCELLED
Đơn COD: PENDING_CONFIRMATION ──Support xác nhận (phân hệ 4)──► CONFIRMED …
          └─khách hủy──► CANCELLED      (CONFIRMED cũng hủy được trước khi đóng gói)

Lượt QR : PENDING → PAID | FAILED | EXPIRED | CANCELLED
Lượt COD: UNPAID → PAID (phân hệ 4 khi thu tiền) | CANCELLED
Giữ tồn : ACTIVE → CONSUMED (đóng gói, phân hệ 4) | RELEASED
Voucher : RESERVED → CONSUMED | RELEASED
```

## 6. Mã lỗi

| HTTP | Mã | Khi nào |
|---|---|---|
| 404 | `ADDRESS_NOT_FOUND`, `ORDER_NOT_FOUND`, `PAYMENT_ATTEMPT_NOT_FOUND`, `VOUCHER_NOT_FOUND` | Không tồn tại hoặc không thuộc người gọi |
| 409 | `PRODUCT_UNAVAILABLE`, `INSUFFICIENT_STOCK`, `PRICE_CHANGED` | Hàng/giá thay đổi |
| 409 | `VOUCHER_USAGE_LIMIT_REACHED`, `VOUCHER_CODE_EXISTS`, `VOUCHER_IN_USE`, `VOUCHER_LIMIT_BELOW_USAGE` | Xung đột voucher |
| 409 | `ORDER_NOT_CANCELLABLE`, `PAYMENT_ATTEMPT_NOT_PENDING`, `PAYMENT_ATTEMPT_EXPIRED`, `PAYMENT_ATTEMPT_ALREADY_PENDING`, `PAYMENT_RETRY_NOT_ALLOWED`, `PAYMENT_ATTEMPTS_EXHAUSTED` | Sai trạng thái |
| 422 | `CART_EMPTY`, `VOUCHER_INVALID`, `VOUCHER_NOT_ACTIVE`, `VOUCHER_MIN_ORDER_NOT_MET`, `VOUCHER_RULE_INVALID` | Vi phạm quy tắc nghiệp vụ |

## 7. Tiêu chí nghiệm thu

1. Đặt QR thành công: đơn `AWAITING_PAYMENT`, `stock_reserved` tăng đúng số lượng, `stock_on_hand` **không đổi**, giỏ `CONVERTED`.
2. Đặt COD: đơn `PENDING_CONFIRMATION`, lượt `UNPAID`.
3. Bấm đặt hai lần: chỉ một đơn, tồn chỉ giữ một lần.
4. Hết hàng hoặc sản phẩm ngừng bán: không tạo đơn, không giữ tồn, giỏ vẫn `ACTIVE`.
5. Voucher sai/chưa đủ điều kiện/hết lượt: báo đúng mã lỗi, không giữ tồn.
6. QR thành công: đơn `CONFIRMED`, voucher `CONSUMED`.
7. QR thất bại hoặc hết hạn: tồn và voucher được trả; tạo lại QR được tối đa 3 lượt; hết lượt thì đơn tự hủy.
8. Hủy đơn trước đóng gói: tồn và voucher được trả; đơn đã thanh toán có bản ghi hoàn tiền mô phỏng `PENDING`.
9. Khách không xem/hủy/thanh toán được đơn của người khác (`404`).
10. Manager có quyền tạo/sửa/xóa voucher; vai trò khác và Admin bị từ chối (`403`).

## 8. Phụ thuộc và ngoài phạm vi

- **Giỏ hàng** (thêm/sửa/xóa sản phẩm, gộp giỏ khi đăng nhập) thuộc phân hệ 2. Trong lúc chờ, dùng [`database/seed/dev_fill_cart.sql`](../../database/seed/dev_fill_cart.sql) để bỏ hàng vào giỏ khi test.
- Support xác nhận COD, đóng gói (trừ `stock_on_hand`, `CONSUMED` giữ tồn), giao hàng và thu COD thuộc phân hệ 4.
- Hoàn tất hoàn tiền thuộc phân hệ 5.
