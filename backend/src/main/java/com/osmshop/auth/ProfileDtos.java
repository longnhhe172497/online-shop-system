package com.osmshop.auth;

import com.osmshop.validation.UserInputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record ProfileResponse(long id, String email, String fullName, String phone, String role) {}

    public record ProfileUpdateRequest(
            @NotBlank
            @Size(max = 150)
            @Pattern(regexp = UserInputRules.PERSON_NAME,
                    message = "Họ và tên chỉ được chứa chữ cái và khoảng trắng")
            String fullName,
            @Pattern(regexp = UserInputRules.OPTIONAL_PHONE,
                    message = "Số điện thoại phải gồm đúng 10 chữ số")
            String phone) {}

    public record AddressRequest(
            @NotBlank @Size(max = 150)
            @Pattern(regexp = UserInputRules.PERSON_NAME,
                    message = "Họ tên người nhận chỉ được chứa chữ cái và khoảng trắng")
            String recipientName,
            @NotBlank
            @Pattern(regexp = UserInputRules.REQUIRED_PHONE,
                    message = "Số điện thoại phải gồm đúng 10 chữ số")
            String phone,
            @NotBlank @Size(max = 300) String addressLine,
            @Size(max = 120) String ward,
            @Size(max = 120) String district,
            @NotBlank @Size(max = 120) String province,
            boolean isDefault) {}

    public record AddressResponse(
            long id, String recipientName, String phone, String addressLine,
            String ward, String district, String province, boolean isDefault) {}
}
