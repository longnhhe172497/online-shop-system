package com.osmshop.auth;

import com.osmshop.auth.AuthDtos.LoginRequest;
import com.osmshop.auth.AuthDtos.LoginResponse;
import com.osmshop.auth.AuthDtos.PasswordResetRequest;
import com.osmshop.auth.AuthDtos.PasswordResetConfirmRequest;
import com.osmshop.auth.AuthDtos.PasswordResetConfirmResponse;
import com.osmshop.auth.AuthDtos.RegisterRequest;
import com.osmshop.auth.AuthDtos.RegisterResponse;
import com.osmshop.auth.AuthDtos.VerifyRequest;
import com.osmshop.auth.AuthDtos.VerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest request) {
        return auth.verify(request.token());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        auth.requestPasswordReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    public PasswordResetConfirmResponse confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        return auth.confirmPasswordReset(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthPrincipal user,
                       @RequestHeader("Authorization") String authorization) {
        auth.logout(user.id(), authorization.substring(7).trim());
    }
}
