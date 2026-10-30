# Kiểm thử phần Tài khoản và Admin

Tài liệu này phân biệt rõ **unit test** (logic độc lập), **integration test** (API + Spring + PostgreSQL), **UI test** (React với API giả lập) và **E2E** (trình duyệt + API + PostgreSQL + MailHog). Không dùng tổng số test để khẳng định 100% nhánh code đã được bao phủ.

Các unit test service thuần kiểu Mockito (`when` → gọi service → `assert`/`verify`) nằm tại `backend/src/test/java/com/osmshop/`:

| Service | File test | Số ca |
| --- | --- | ---: |
| `AuthService` | `auth/AuthServiceUnitTests.java` | 40 |
| `EmailChangeService` | `auth/EmailChangeServiceUnitTests.java` | 6 |
| `GoogleIdentityService` | `auth/GoogleIdentityServiceUnitTests.java` | 11 |
| `ProfileService` | `auth/ProfileServiceUnitTests.java` | 12 |
| `AdminService` | `admin/AdminServiceUnitTests.java` | 13 |
| `InvitationService` | `admin/InvitationServiceUnitTests.java` | 8 |
| `RolePermissionService` | `admin/RolePermissionServiceUnitTests.java` | 8 |

Những test này mock `JdbcTemplate` (dự án hiện dùng SQL trực tiếp thay vì `UserRepository`) và các phụ thuộc khác, không cần chạy PostgreSQL hay SMTP. Các test `*FlowTests`/`*BoundaryTests` bên dưới là test tích hợp, không gọi là unit test thuần khi chụp bằng chứng.

| Nhóm chức năng | Backend | Frontend / E2E |
| --- | --- | --- |
| Đăng ký, xác minh, gửi lại email, trùng email, token hết hạn | `AuthFlowTests`, `AuthBoundaryTests` | `App.test.tsx`, `auth-admin.spec.ts` |
| Đăng nhập bearer/cookie, đăng xuất, CSRF, tài khoản bị khóa | `AuthFlowTests`, `AuthBoundaryTests` | `App.test.tsx`, `auth-admin.spec.ts` |
| Quên/đặt lại mật khẩu, token sai/hết hạn, thu hồi phiên | `AuthFlowTests`, `AuthAdvancedFlowTests`, `AuthBoundaryTests` | `App.test.tsx` |
| Giới hạn số lần thử | `AuthRateLimiterTests` (unit), `AuthFlowTests` | — |
| Google đăng nhập, liên kết/hủy liên kết, đổi email | `GoogleIdentityFlowTests`, `EmailChangeBoundaryTests` | `GoogleButton.test.tsx`, `AccountSecurityPage.test.tsx`, `EmailChangePages.test.tsx` |
| MFA email/TOTP, mã khôi phục, mã dùng lại, tắt MFA | `AuthAdvancedFlowTests`, `TotpServiceTests` (unit) | `AccountSecurityPage.test.tsx`, `App.test.tsx`, `auth-admin.spec.ts` |
| Hồ sơ và sổ địa chỉ, mặc định, giới hạn, quyền sở hữu | `ProfileFlowTests`, `ProfileBoundaryTests` | `ProfilePage.test.tsx`, `App.test.tsx` |
| Thiết bị/phiên đăng nhập, thu hồi phiên khác | `AuthAdvancedFlowTests`, `ProfileBoundaryTests` | `AccountSessionsPage.test.tsx`, `App.test.tsx` |
| Admin: danh sách, tìm kiếm, sửa, vai trò/trạng thái, bảo vệ Admin cuối | `AdminFlowTests`, `AdminBoundaryTests` | `AdminPage.test.tsx`, `App.test.tsx` |
| Admin: lời mời nhân viên, gửi lại/thu hồi/chấp nhận | `AdminFlowTests`, `AdminBoundaryTests` | `AdminPage.test.tsx`, `App.test.tsx`, `auth-admin.spec.ts` |
| Admin: phân quyền vai trò, phiên bản và quyền có hiệu lực ngay | `AdminFlowTests`, `AdminBoundaryTests` | `RolePermissionsPage.test.tsx`, `auth-admin.spec.ts` |
| Admin: thiết lập, nhật ký, dashboard và điều hướng nội bộ | `AdminFlowTests`, `AdminBoundaryTests` | `AdminPage.test.tsx`, `App.test.tsx` |

## Chạy test

Backend cần PostgreSQL local theo cấu hình dự án:

```powershell
cd C:\DOAN\Online-Shop-System\backend
.\mvnw.cmd verify
```

Chỉ chạy các unit test service mới (không cần database):

```powershell
cd C:\DOAN\Online-Shop-System\backend
.\mvnw.cmd '-Dtest=*ServiceUnitTests' test
```

Đo coverage riêng cho unit test service:

```powershell
.\mvnw.cmd org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent '-Dtest=*ServiceUnitTests' test org.jacoco:jacoco-maven-plugin:0.8.13:report
```

Mở `backend/target/site/jacoco/index.html` để xem nhánh đã/ chưa đi qua. Coverage của riêng unit test không đại diện cho coverage của toàn bộ suite tích hợp; không gọi 98 ca này là “mọi trường hợp” khi các nhánh chưa được xác nhận.

Frontend:

```powershell
cd C:\DOAN\Online-Shop-System\frontend
npm run lint
npm run build
npm test
```

E2E dùng database và MailHog riêng (không gửi email thật):

```powershell
cd C:\DOAN\Online-Shop-System
docker compose -f docker-compose.e2e.yml up -d --wait
cd frontend
npm run test:e2e
cd ..
docker compose -f docker-compose.e2e.yml down
```

## Giới hạn kiểm thử tự động

- Email thật qua Gmail SMTP không được gửi trong test; các test backend giả lập bộ gửi mail và E2E dùng MailHog để không làm phiền người nhận.
- Google Identity Services thật và token OAuth thật không chạy trong CI; bộ xác thực Google được giả lập ở test backend, nút Google được giả lập trong UI test.
- Dashboard tổng quan hiện là giao diện tĩnh; test chỉ xác nhận điều hướng và phân quyền, không xác nhận số liệu kinh doanh chưa được triển khai.
- Sau khi cập nhật nhánh này từ `develop`, cần chạy lại toàn bộ suite và bổ sung test tích hợp cho bất kỳ API/permission mới nào được merge từ phần việc của thành viên khác.
