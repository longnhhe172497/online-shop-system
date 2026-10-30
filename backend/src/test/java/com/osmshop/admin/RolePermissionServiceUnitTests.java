package com.osmshop.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.osmshop.auth.AuthApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** Pure service tests for editable internal-role permissions. */
class RolePermissionServiceUnitTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RolePermissionService service = new RolePermissionService(jdbc);

    @Test
    void permissionsFor_customerRole_returnsEmptyWithoutDatabaseAccess() {
        assertEquals(List.of(), service.permissionsFor("CUSTOMER"));
        verify(jdbc, never()).queryForList(anyString(), eq(String.class), eq("CUSTOMER"));
    }

    @Test
    void permissionsFor_internalRole_returnsStoredPermissions() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("WAREHOUSE")))
                .thenReturn(List.of("WAREHOUSE_INVENTORY_VIEW"));

        assertEquals(List.of("WAREHOUSE_INVENTORY_VIEW"), service.permissionsFor("WAREHOUSE"));
        verify(jdbc).queryForList(anyString(), eq(String.class), eq("WAREHOUSE"));
    }

    @Test
    void replace_adminRole_rejectedBeforeDatabaseAccess() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.replace(1L, "ADMIN", new RolePermissionService.UpdatePermissions(0, List.of())));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertEquals("ROLE_NOT_EDITABLE", error.code());
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), eq("ADMIN"));
    }

    @Test
    void replace_duplicatePermission_rejectedBeforeWrite() {
        var request = new RolePermissionService.UpdatePermissions(0,
                List.of("WAREHOUSE_INVENTORY_VIEW", "WAREHOUSE_INVENTORY_VIEW"));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.replace(1L, "WAREHOUSE", request));

        assertEquals("INVALID_PERMISSION", error.code());
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), eq("WAREHOUSE"));
    }

    @Test
    void replace_staleVersion_rejectedWithoutChangingGrants() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("WAREHOUSE"))).thenReturn(2);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.replace(1L, "WAREHOUSE",
                        new RolePermissionService.UpdatePermissions(1, List.of("WAREHOUSE_INVENTORY_VIEW"))));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertEquals("PERMISSION_VERSION_CONFLICT", error.code());
        verify(jdbc, never()).update(anyString(), eq("WAREHOUSE"));
    }

    @Test
    void matrix_containsAllFourEditableRoles() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(1);

        var matrix = service.matrix();

        assertEquals(List.of("MANAGER", "SUPPORT", "WAREHOUSE", "DELIVERY"),
                matrix.roles().stream().map(RolePermissionService.RoleGrant::role).toList());
        assertEquals(RolePermissionService.CATALOG, matrix.catalog());
    }

    @Test
    void replace_whenPermissionsUnchanged_keepsVersionAndSkipsWrites() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("WAREHOUSE"))).thenReturn(2);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("WAREHOUSE")))
                .thenReturn(List.of("WAREHOUSE_INVENTORY_VIEW"));

        var result = service.replace(1L, "WAREHOUSE", new RolePermissionService.UpdatePermissions(2,
                List.of("WAREHOUSE_INVENTORY_VIEW")));

        assertEquals(2, result.version());
        assertEquals(List.of("WAREHOUSE_INVENTORY_VIEW"), result.permissions());
        verify(jdbc, never()).update(anyString(), eq("WAREHOUSE"));
    }

    @Test
    void replace_whenPermissionsChange_incrementsVersionAndAudits() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("WAREHOUSE"))).thenReturn(2, 3);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("WAREHOUSE")))
                .thenReturn(List.of("WAREHOUSE_INVENTORY_VIEW"), List.of("WAREHOUSE_INVENTORY_ADJUST"));

        var result = service.replace(1L, "WAREHOUSE", new RolePermissionService.UpdatePermissions(2,
                List.of("WAREHOUSE_INVENTORY_ADJUST")));

        assertEquals(3, result.version());
        assertEquals(List.of("WAREHOUSE_INVENTORY_ADJUST"), result.permissions());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("DELETE FROM role_permissions"), eq("WAREHOUSE"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO role_permissions"),
                eq("WAREHOUSE"), eq("WAREHOUSE_INVENTORY_ADJUST"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE role_permission_sets"), eq(1L), eq("WAREHOUSE"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO audit_logs"),
                eq(1L), eq("WAREHOUSE"), eq("WAREHOUSE_INVENTORY_VIEW"), eq("WAREHOUSE_INVENTORY_ADJUST"));
    }
}
