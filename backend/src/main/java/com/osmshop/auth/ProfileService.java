package com.osmshop.auth;

import com.osmshop.auth.ProfileDtos.AddressRequest;
import com.osmshop.auth.ProfileDtos.AddressResponse;
import com.osmshop.auth.ProfileDtos.ProfileResponse;
import com.osmshop.auth.ProfileDtos.ProfileUpdateRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final JdbcTemplate jdbc;

    public ProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProfileResponse getProfile(long userId) {
        return jdbc.query("""
                SELECT id,email,full_name,phone,role FROM users WHERE id = ?
                """, (rs, row) -> new ProfileResponse(rs.getLong("id"), rs.getString("email"),
                        rs.getString("full_name"), rs.getString("phone"), rs.getString("role")), userId)
                .stream().findFirst().orElseThrow(() -> notFound("Profile not found"));
    }

    @Transactional
    public ProfileResponse updateProfile(long userId, ProfileUpdateRequest request) {
        jdbc.update("""
                UPDATE users SET full_name = ?, phone = ?, updated_at = now() WHERE id = ?
                """, request.fullName().trim(), normalizeOptional(request.phone()), userId);
        return getProfile(userId);
    }

    public List<AddressResponse> listAddresses(long userId) {
        return jdbc.query("""
                SELECT id,recipient_name,phone,address_line,ward,district,province,is_default
                FROM user_addresses WHERE user_id = ? ORDER BY is_default DESC, id
                """, (rs, row) -> new AddressResponse(rs.getLong("id"), rs.getString("recipient_name"),
                        rs.getString("phone"), rs.getString("address_line"), rs.getString("ward"),
                        rs.getString("district"), rs.getString("province"), rs.getBoolean("is_default")), userId);
    }

    public int maxSavedAddresses() {
        Integer limit = jdbc.queryForObject("""
                SELECT value_text::integer FROM system_settings WHERE setting_key='max_saved_addresses'
                """, Integer.class);
        return limit == null ? 10 : limit;
    }

    @Transactional
    public AddressResponse createAddress(long userId, AddressRequest request) {
        lockUser(userId);
        Long count = jdbc.queryForObject("SELECT count(*) FROM user_addresses WHERE user_id = ?",
                Long.class, userId);
        Integer limit = jdbc.queryForObject("""
                SELECT value_text::integer FROM system_settings WHERE setting_key = 'max_saved_addresses'
                """, Integer.class);
        if (count != null && limit != null && count >= limit) {
            throw new AuthApiException(HttpStatus.CONFLICT, "ADDRESS_LIMIT_REACHED",
                    "Maximum number of saved addresses reached");
        }
        boolean makeDefault = count == null || count == 0 || request.isDefault();
        if (makeDefault) clearDefault(userId);
        Long id = jdbc.queryForObject("""
                INSERT INTO user_addresses(user_id,recipient_name,phone,address_line,ward,district,province,is_default)
                VALUES (?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, userId, request.recipientName().trim(), request.phone().trim(),
                request.addressLine().trim(), normalizeOptional(request.ward()),
                normalizeOptional(request.district()), request.province().trim(), makeDefault);
        return getAddress(userId, id);
    }

    @Transactional
    public AddressResponse updateAddress(long userId, long addressId, AddressRequest request) {
        lockUser(userId);
        AddressResponse existing = getAddress(userId, addressId);
        boolean makeDefault = request.isDefault() || existing.isDefault();
        if (request.isDefault()) clearDefault(userId);
        jdbc.update("""
                UPDATE user_addresses SET recipient_name = ?, phone = ?, address_line = ?, ward = ?,
                    district = ?, province = ?, is_default = ?, updated_at = now()
                WHERE id = ? AND user_id = ?
                """, request.recipientName().trim(), request.phone().trim(), request.addressLine().trim(),
                normalizeOptional(request.ward()), normalizeOptional(request.district()),
                request.province().trim(), makeDefault, addressId, userId);
        return getAddress(userId, addressId);
    }

    @Transactional
    public void deleteAddress(long userId, long addressId) {
        lockUser(userId);
        List<Boolean> deleted = jdbc.query("""
                DELETE FROM user_addresses WHERE id = ? AND user_id = ? RETURNING is_default
                """, (rs, row) -> rs.getBoolean("is_default"), addressId, userId);
        if (deleted.isEmpty()) throw notFound("Address not found");
        if (deleted.getFirst()) {
            jdbc.update("""
                    UPDATE user_addresses SET is_default = true, updated_at = now()
                    WHERE id = (SELECT id FROM user_addresses WHERE user_id = ? ORDER BY id LIMIT 1)
                    """, userId);
        }
    }

    private AddressResponse getAddress(long userId, long addressId) {
        return jdbc.query("""
                SELECT id,recipient_name,phone,address_line,ward,district,province,is_default
                FROM user_addresses WHERE id = ? AND user_id = ?
                """, (rs, row) -> new AddressResponse(rs.getLong("id"), rs.getString("recipient_name"),
                        rs.getString("phone"), rs.getString("address_line"), rs.getString("ward"),
                        rs.getString("district"), rs.getString("province"), rs.getBoolean("is_default")),
                addressId, userId).stream().findFirst().orElseThrow(() -> notFound("Address not found"));
    }

    private void lockUser(long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
    }

    private void clearDefault(long userId) {
        jdbc.update("""
                UPDATE user_addresses SET is_default = false, updated_at = now()
                WHERE user_id = ? AND is_default = true
                """, userId);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AuthApiException notFound(String message) {
        return new AuthApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
