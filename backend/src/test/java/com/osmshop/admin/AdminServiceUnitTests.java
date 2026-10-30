package com.osmshop.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.osmshop.admin.AdminService.UserView;
import com.osmshop.auth.AuthApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Pure service tests: no Spring context, HTTP layer or database. */
class AdminServiceUnitTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminService service = new AdminService(jdbc);

    @Test
    void searchUsers_withStatusFilter_returnsMatched() {
        UserView account = user(7L, "ACTIVE");
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(account));

        var result = service.users("  Long  ", "ADMIN", "ACTIVE", 0, 20);

        assertEquals(1, result.totalItems());
        assertEquals(List.of(account), result.items());
        verify(jdbc).queryForObject(anyString(), eq(Long.class), eq("%long%"), eq("%long%"),
                eq("ADMIN"), eq("ADMIN"), eq("ACTIVE"), eq("ACTIVE"));
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(),
                eq("%long%"), eq("%long%"), eq("ADMIN"), eq("ADMIN"),
                eq("ACTIVE"), eq("ACTIVE"), eq(20), eq(0));
    }

    @Test
    void getUserById_whenNotFound_throws404() {
        AuthApiException error = assertThrows(AuthApiException.class, () -> service.userDetail(99L));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        assertEquals("NOT_FOUND", error.code());
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(99L));
    }

    @Test
    void searchUsers_whenPageInvalid_rejectsBeforeQuery() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.users("", "", "", -1, 20));

        assertEquals("INVALID_PAGE", error.code());
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void updateUser_whenMissing_doesNotWriteAudit() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.update(1L, 99L, new AdminController.UpdateUser("  New Name  ", null)));

        assertEquals("NOT_FOUND", error.code());
        verify(jdbc).update(anyString(), eq("New Name"), eq(null), eq(99L));
        verify(jdbc, never()).update(anyString(), eq(1L), eq("USER_UPDATED"), any(), any(), any(), any());
    }

    @Test
    void changeSetting_whenValueOutOfRange_rejectsBeforeWrite() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeSetting(1L, "max_saved_addresses", "21"));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertEquals("INVALID_SETTING", error.code());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void audit_whenDateRangeIsReversed_rejectsBeforeQuery() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.audit(0, 20, "", "", LocalDate.of(2026, 10, 17),
                        LocalDate.of(2026, 10, 16)));

        assertEquals("INVALID_DATE_RANGE", error.code());
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void changeRole_whenUnknownRole_rejectedBeforeLock() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeRole(1L, 7L, "OWNER"));

        assertEquals("INVALID_ROLE", error.code());
        verify(jdbc, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L));
    }

    @Test
    void changeRole_whenLastActiveAdmin_rejectedWithoutChangingRole() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L)))
                .thenReturn(List.of(user(7L, "ACTIVE")));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeRole(1L, 7L, "CUSTOMER"));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertEquals("LAST_ADMIN", error.code());
        verify(jdbc, never()).update(anyString(), eq("CUSTOMER"), eq(7L));
    }

    @Test
    void changeStatus_whenUnverifiedCannotBecomeActive() {
        UserView unverified = new UserView(7L, "long@example.com", "Long", null,
                "CUSTOMER", "PENDING_VERIFICATION", OffsetDateTime.now(), null);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L)))
                .thenReturn(List.of(unverified));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeStatus(1L, 7L, "ACTIVE"));

        assertEquals("EMAIL_NOT_VERIFIED", error.code());
        verify(jdbc, never()).update(anyString(), eq("ACTIVE"), eq(7L));
    }

    @Test
    void changeStatus_whenVerifiedCannotReturnToPending() {
        UserView verified = new UserView(7L, "long@example.com", "Long", null,
                "CUSTOMER", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now());
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L)))
                .thenReturn(List.of(verified));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeStatus(1L, 7L, "PENDING_VERIFICATION"));

        assertEquals("INVALID_STATUS_TRANSITION", error.code());
        verify(jdbc, never()).update(anyString(), eq("PENDING_VERIFICATION"), eq(7L));
    }

    @Test
    void changeStatus_whenUnknownStatus_rejectedBeforeLock() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeStatus(1L, 7L, "DELETED"));

        assertEquals("INVALID_STATUS", error.code());
    }

    @Test
    void changeStatus_whenLastActiveAdminCannotBeLocked() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L)))
                .thenReturn(List.of(user(7L, "ACTIVE")));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeStatus(1L, 7L, "LOCKED"));

        assertEquals("LAST_ADMIN", error.code());
        verify(jdbc, never()).update(anyString(), eq("LOCKED"), eq(7L));
    }

    @Test
    void changeStatus_whenCustomerIsLocked_revokesSessions() {
        UserView active = new UserView(7L, "long@example.com", "Long", null, "CUSTOMER", "ACTIVE",
                OffsetDateTime.now(), OffsetDateTime.now());
        UserView locked = new UserView(7L, "long@example.com", "Long", null, "CUSTOMER", "LOCKED",
                active.createdAt(), active.verifiedAt());
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L)))
                .thenReturn(List.of(active), List.of(locked));

        assertEquals("LOCKED", service.changeStatus(1L, 7L, " locked ").status());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO audit_logs"),
                eq(1L), eq("USER_STATUS_CHANGED"), eq("USER"), eq("7"), eq("status"), eq("LOCKED"));
    }

    @Test
    void changeRole_whenRoleUnchanged_doesNotRevokeSessions() {
        UserView active = new UserView(7L, "long@example.com", "Long", null, "CUSTOMER", "ACTIVE",
                OffsetDateTime.now(), OffsetDateTime.now());
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L)))
                .thenReturn(List.of(active));

        assertEquals("CUSTOMER", service.changeRole(1L, 7L, "customer").role());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"), eq(7L));
    }

    @Test
    void changeRole_whenPromotedToStaff_revokesPreviousSessions() {
        UserView customer = new UserView(7L, "long@example.com", "Long", null, "CUSTOMER", "ACTIVE",
                OffsetDateTime.now(), OffsetDateTime.now());
        UserView support = new UserView(7L, "long@example.com", "Long", null, "SUPPORT", "ACTIVE",
                customer.createdAt(), customer.verifiedAt());
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L)))
                .thenReturn(List.of(customer), List.of(support));

        assertEquals("SUPPORT", service.changeRole(1L, 7L, "support").role());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"), eq(7L));
    }

    @Test
    void revokeSessions_whenUserMissing_rejectsWithoutRevoking() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.revokeSessions(1L, 99L));

        assertEquals("NOT_FOUND", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"), eq(99L));
    }

    @Test
    void revokeSessions_whenUserExists_revokesAndAudits() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UserView>>any(), eq(7L)))
                .thenReturn(List.of(user(7L, "ACTIVE")));

        service.revokeSessions(1L, 7L);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_sessions"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO audit_logs"),
                eq(1L), eq("USER_SESSIONS_REVOKED"), eq("USER"), eq("7"), eq("reason"), eq("admin"));
    }

    @Test
    void changeSetting_whenUnknownKey_returns404() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeSetting(1L, "unrecognized_setting", "1"));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    void changeSetting_whenTimeZoneUnsupported_rejects() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeSetting(1L, "reporting_time_zone", "UTC"));

        assertEquals("INVALID_SETTING", error.code());
    }

    @Test
    void changeSetting_whenNumericValueNotANumber_rejects() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeSetting(1L, "max_saved_addresses", "abc"));

        assertEquals("INVALID_SETTING", error.code());
    }

    @Test
    void changeSetting_whenUpdateAffectsNoRow_returns404WithoutAudit() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.changeSetting(1L, "max_saved_addresses", "10"));

        assertEquals("NOT_FOUND", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO audit_logs"),
                eq(1L), eq("SETTING_CHANGED"), eq("SYSTEM_SETTING"), eq("max_saved_addresses"),
                eq("value"), eq("10"));
    }

    @Test
    void changeSetting_whenValid_savesAndAudits() {
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("UPDATE system_settings"),
                eq("10"), eq(1L), eq("max_saved_addresses"))).thenReturn(1);
        var expected = new AdminService.SettingView("max_saved_addresses", "10", "Limit", OffsetDateTime.now());
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM system_settings WHERE setting_key"),
                org.mockito.ArgumentMatchers.<RowMapper<AdminService.SettingView>>any(),
                eq("max_saved_addresses"))).thenReturn(expected);

        assertEquals(expected, service.changeSetting(1L, "max_saved_addresses", "10"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO audit_logs"),
                eq(1L), eq("SETTING_CHANGED"), eq("SYSTEM_SETTING"), eq("max_saved_addresses"),
                eq("value"), eq("10"));
    }

    @Test
    void searchUsers_whenPageSizeExceedsMaximum_rejected() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.users("", "", "", 0, 101));

        assertEquals("INVALID_PAGE", error.code());
    }

    private static UserView user(long id, String status) {
        return new UserView(id, "long@example.com", "Long", null, "ADMIN", status,
                OffsetDateTime.parse("2026-10-17T00:00:00+07:00"), null);
    }
}
