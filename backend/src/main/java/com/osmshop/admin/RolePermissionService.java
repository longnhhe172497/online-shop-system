package com.osmshop.admin;

import com.osmshop.auth.AuthApiException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolePermissionService {
    public static final List<PermissionDefinition> CATALOG = List.of(
            new PermissionDefinition("MANAGER_ORDER_VIEW", "Xem đơn hàng", "Đơn hàng", "GET /manager/orders"),
            new PermissionDefinition("MANAGER_ORDER_ASSIGN", "Phân công giao hàng", "Đơn hàng", "POST /manager/orders/{id}/assign"),
            new PermissionDefinition("MANAGER_RETURN_DECIDE", "Quyết định trả hàng", "Hậu mãi", "POST /manager/returns/{id}/decision"),
            new PermissionDefinition("SUPPORT_ORDER_VIEW", "Xem đơn chờ xác nhận", "Đơn hàng", "GET /support/orders/pending"),
            new PermissionDefinition("SUPPORT_ORDER_CONFIRM", "Xác nhận đơn COD", "Đơn hàng", "POST /support/orders/{id}/confirm"),
            new PermissionDefinition("WAREHOUSE_PACK_VIEW", "Xem hàng cần đóng gói", "Kho", "GET /warehouse/packing-queue"),
            new PermissionDefinition("WAREHOUSE_PACK", "Đóng gói đơn hàng", "Kho", "POST /warehouse/orders/{id}/pack"),
            new PermissionDefinition("WAREHOUSE_INVENTORY_VIEW", "Xem tồn kho", "Kho", "GET /warehouse/inventory"),
            new PermissionDefinition("WAREHOUSE_INVENTORY_ADJUST", "Điều chỉnh tồn kho", "Kho", "POST /warehouse/inventory/adjustments"),
            new PermissionDefinition("WAREHOUSE_RETURN_INSPECT", "Kiểm tra hàng trả", "Hậu mãi", "POST /warehouse/returns/{id}/inspection"),
            new PermissionDefinition("DELIVERY_ASSIGNMENT_VIEW", "Xem chuyến giao được phân", "Giao hàng", "GET /delivery/assignments"),
            new PermissionDefinition("DELIVERY_ATTEMPT_UPDATE", "Cập nhật lần giao", "Giao hàng", "PATCH /delivery/attempts/{id}"));
    private static final List<String> EDITABLE_ROLES = List.of("MANAGER", "SUPPORT", "WAREHOUSE", "DELIVERY");
    private static final Set<String> CODES = CATALOG.stream().map(PermissionDefinition::code)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private final JdbcTemplate jdbc;

    public RolePermissionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RolePermissionMatrix matrix() {
        List<RoleGrant> grants = new ArrayList<>();
        for (String role : EDITABLE_ROLES) grants.add(grant(role));
        return new RolePermissionMatrix(CATALOG, grants);
    }

    public List<String> permissionsFor(String role) {
        if (!EDITABLE_ROLES.contains(role)) return List.of();
        return jdbc.queryForList("SELECT permission_code FROM role_permissions WHERE role=? ORDER BY permission_code",
                String.class, role);
    }

    @Transactional
    public RoleGrant replace(long actorId, String role, UpdatePermissions request) {
        if (!EDITABLE_ROLES.contains(role)) throw new AuthApiException(HttpStatus.BAD_REQUEST,
                "ROLE_NOT_EDITABLE", "Only internal business roles can be configured");
        if (request.permissions() == null || request.permissions().stream().anyMatch(code -> !CODES.contains(code))
                || request.permissions().size() != new LinkedHashSet<>(request.permissions()).size())
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_PERMISSION", "Unknown or duplicate permission");
        Integer version = jdbc.queryForObject("SELECT version FROM role_permission_sets WHERE role=? FOR UPDATE",
                Integer.class, role);
        if (version == null || !version.equals(request.version())) throw new AuthApiException(HttpStatus.CONFLICT,
                "PERMISSION_VERSION_CONFLICT", "Permissions changed; reload before saving");
        List<String> before = permissionsFor(role);
        List<String> after = CATALOG.stream().map(PermissionDefinition::code)
                .filter(request.permissions()::contains).toList();
        if (before.equals(after.stream().sorted().toList())) return grant(role);
        jdbc.update("DELETE FROM role_permissions WHERE role=?", role);
        for (String code : after) jdbc.update("INSERT INTO role_permissions(role,permission_code) VALUES (?,?)", role, code);
        jdbc.update("UPDATE role_permission_sets SET version=version+1,updated_at=now(),updated_by=? WHERE role=?",
                actorId, role);
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details)
                VALUES (?,'ROLE_PERMISSIONS_CHANGED','ROLE',?,jsonb_build_object('before',?::text,'after',?::text))
                """, actorId, role, String.join(",", before), String.join(",", after));
        return grant(role);
    }

    private RoleGrant grant(String role) {
        Integer version = jdbc.queryForObject("SELECT version FROM role_permission_sets WHERE role=?",
                Integer.class, role);
        return new RoleGrant(role, version == null ? 0 : version, permissionsFor(role));
    }

    public record PermissionDefinition(String code, String label, String group, String endpoint) {}
    public record RoleGrant(String role, int version, List<String> permissions) {}
    public record RolePermissionMatrix(List<PermissionDefinition> catalog, List<RoleGrant> roles) {}
    public record UpdatePermissions(@NotNull @Min(0) Integer version, @NotNull List<String> permissions) {}
}
