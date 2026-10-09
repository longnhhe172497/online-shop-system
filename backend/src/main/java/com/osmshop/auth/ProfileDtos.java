package com.osmshop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record ProfileResponse(long id, String email, String fullName, String phone, String role) {}

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 30) String phone) {}

    public record AddressRequest(
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Size(max = 300) String addressLine,
            @Size(max = 120) String ward,
            @Size(max = 120) String district,
            @NotBlank @Size(max = 120) String province,
            boolean isDefault) {}

    public record AddressResponse(
            long id, String recipientName, String phone, String addressLine,
            String ward, String district, String province, boolean isDefault) {}
}
