package com.osmshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.osmshop.auth.ProfileDtos.AddressRequest;
import com.osmshop.auth.ProfileDtos.AddressResponse;
import com.osmshop.auth.ProfileDtos.ProfileResponse;
import com.osmshop.auth.ProfileDtos.ProfileUpdateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProfileServiceUnitTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProfileService service = new ProfileService(jdbc);

    @Test
    void getProfile_whenUserMissing_throws404() {
        AuthApiException error = assertThrows(AuthApiException.class, () -> service.getProfile(99L));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProfileResponse>>any(), eq(99L));
    }

    @Test
    void updateProfile_trimsNameAndTreatsBlankPhoneAsAbsent() {
        ProfileResponse profile = new ProfileResponse(7L, "long@example.com", "Long", null, "CUSTOMER");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProfileResponse>>any(), eq(7L)))
                .thenReturn(List.of(profile));

        ProfileResponse result = service.updateProfile(7L, new ProfileUpdateRequest("  Long  ", "   "));

        assertEquals(profile, result);
        verify(jdbc).update(anyString(), eq("Long"), eq(null), eq(7L));
    }

    @Test
    void updateProfile_whenAccountMissing_returns404() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.updateProfile(99L, new ProfileUpdateRequest("Long", null)));

        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    void listAddresses_onlyQueriesCurrentOwner() {
        AddressResponse address = saved(true);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AddressResponse>>any(), eq(7L)))
                .thenReturn(List.of(address));

        assertEquals(List.of(address), service.listAddresses(7L));
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AddressResponse>>any(), eq(7L));
    }

    @Test
    void maxSavedAddresses_whenSettingMissing_usesSafeDefault() {
        assertEquals(10, service.maxSavedAddresses());
        verify(jdbc).queryForObject(anyString(), eq(Integer.class));
    }

    @Test
    void createAddress_whenLimitReached_doesNotInsert() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(7L, 10L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(10);

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.createAddress(7L, address(false)));

        assertEquals("ADDRESS_LIMIT_REACHED", error.code());
        verify(jdbc, never()).queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO user_addresses"),
                eq(Long.class), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createAddress_firstAddressBecomesDefaultEvenWhenUnchecked() {
        AddressResponse saved = saved(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(7L, 0L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(10);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO user_addresses"),
                eq(Long.class), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(11L);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AddressResponse>>any(),
                eq(11L), eq(7L))).thenReturn(List.of(saved));

        AddressResponse result = service.createAddress(7L, address(false));

        assertEquals(saved, result);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET is_default = false"), eq(7L));
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO user_addresses"),
                eq(Long.class), eq(7L), eq("Long"), eq("0901234567"), eq("123 Street"),
                eq(null), eq(null), eq("Hanoi"), eq(true));
    }

    @Test
    void createAddress_additionalUncheckedAddress_remainsNonDefault() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(7L, 1L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(10);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO user_addresses"),
                eq(Long.class), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(11L);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AddressResponse>>any(),
                eq(11L), eq(7L))).thenReturn(List.of(saved(false)));

        assertEquals(false, service.createAddress(7L, address(false)).isDefault());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("SET is_default = false"), eq(7L));
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("INSERT INTO user_addresses"),
                eq(Long.class), eq(7L), eq("Long"), eq("0901234567"), eq("123 Street"),
                eq(null), eq(null), eq("Hanoi"), eq(false));
    }

    @Test
    void updateAddress_cannotUnsetOnlyDefault() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AddressResponse>>any(),
                eq(11L), eq(7L))).thenReturn(List.of(saved(true)));

        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.updateAddress(7L, 11L, address(false)));

        assertEquals("DEFAULT_ADDRESS_REQUIRED", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("UPDATE user_addresses SET recipient_name"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateAddress_whenNotOwned_returns404BeforeWrite() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.updateAddress(7L, 99L, address(true)));

        assertEquals("NOT_FOUND", error.code());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("UPDATE user_addresses SET recipient_name"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateAddress_promotingOtherAddress_clearsPreviousDefault() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AddressResponse>>any(),
                eq(11L), eq(7L))).thenReturn(List.of(saved(false)), List.of(saved(true)));

        assertEquals(true, service.updateAddress(7L, 11L, address(true)).isDefault());

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET is_default = false"), eq(7L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_addresses SET recipient_name"),
                eq("Long"), eq("0901234567"), eq("123 Street"), eq(null), eq(null), eq("Hanoi"),
                eq(true), eq(11L), eq(7L));
    }

    @Test
    void deleteAddress_whenMissing_throws404() {
        AuthApiException error = assertThrows(AuthApiException.class,
                () -> service.deleteAddress(7L, 99L));

        assertEquals("NOT_FOUND", error.code());
        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("DELETE FROM user_addresses"),
                org.mockito.ArgumentMatchers.<RowMapper<Boolean>>any(), eq(99L), eq(7L));
    }

    @Test
    void deleteAddress_whenDefault_promotesNextAddress() {
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("DELETE FROM user_addresses"),
                org.mockito.ArgumentMatchers.<RowMapper<Boolean>>any(), eq(11L), eq(7L)))
                .thenReturn(List.of(true));

        service.deleteAddress(7L, 11L);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET is_default = true"), eq(7L));
    }

    @Test
    void deleteAddress_whenNotDefault_doesNotChangeDefault() {
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("DELETE FROM user_addresses"),
                org.mockito.ArgumentMatchers.<RowMapper<Boolean>>any(), eq(11L), eq(7L)))
                .thenReturn(List.of(false));

        service.deleteAddress(7L, 11L);

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("SET is_default = true"), eq(7L));
    }

    private static AddressRequest address(boolean makeDefault) {
        return new AddressRequest("Long", "0901234567", "123 Street", "", "", "Hanoi", makeDefault);
    }

    private static AddressResponse saved(boolean isDefault) {
        return new AddressResponse(11L, "Long", "0901234567", "123 Street", null, null, "Hanoi", isDefault);
    }
}
