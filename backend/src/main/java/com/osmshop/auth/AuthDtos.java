package com.osmshop.auth;

import com.osmshop.validation.UserInputRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 150)
            @Pattern(regexp = UserInputRules.PERSON_NAME,
                    message = "Họ và tên chỉ được chứa chữ cái và khoảng trắng")
            String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 8, max = 72) String confirmPassword,
            @Pattern(regexp = UserInputRules.OPTIONAL_PHONE,
                    message = "Số điện thoại phải gồm đúng 10 chữ số")
            String phone) {}

    public record RegisterResponse(long userId, boolean verificationRequired, boolean emailSent) {}
    public record VerifyRequest(@NotBlank String token) {}
    public record VerifyResponse(boolean verified) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record PasswordResetRequest(@NotBlank @Email String email) {}
    public record PasswordResetConfirmRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword,
            @NotBlank @Size(min = 8, max = 72) String confirmPassword) {}
    public record PasswordResetConfirmResponse(boolean reset) {}
    public record UserResponse(long id, String email, String fullName, String role) {}
    public record LoginResponse(String accessToken, Instant expiresAt, UserResponse user,
                                boolean mfaRequired, String challengeToken, String mfaMethod,
                                boolean emailDeliveryFailed) {}
    public record BrowserLoginResponse(UserResponse user, boolean mfaRequired, String challengeToken,
                                       String mfaMethod, boolean emailDeliveryFailed) {}
    public record MfaVerifyRequest(@NotBlank String challengeToken, @NotBlank String code) {}
    public record MfaMethodRequest(@NotBlank String challengeToken, @NotBlank String method) {}
    public record MfaDisableRequest(@NotBlank String password) {}
    public record MfaSensitiveRequest(@NotBlank String password, @NotBlank String challengeToken,
                                      @NotBlank String code) {}
    public record MfaStepUpResponse(String challengeToken, String mfaMethod, boolean emailDeliveryFailed) {}
    public record TotpStartResponse(String secret, String otpauthUri) {}
    public record TotpConfirmRequest(@NotBlank String code) {}
    public record MfaRecoveryResponse(List<String> recoveryCodes) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword,
            @NotBlank String confirmPassword) {}
    public record SessionView(long id, String ipAddress, String userAgent, Instant createdAt,
                              Instant lastSeenAt, Instant expiresAt, boolean current) {}
}
