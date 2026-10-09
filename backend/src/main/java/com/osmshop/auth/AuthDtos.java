package com.osmshop.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 8, max = 72) String confirmPassword,
            @Size(max = 30) String phone) {}

    public record RegisterResponse(long userId, boolean verificationRequired) {}
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
    public record LoginResponse(String accessToken, Instant expiresAt, UserResponse user) {}
}
