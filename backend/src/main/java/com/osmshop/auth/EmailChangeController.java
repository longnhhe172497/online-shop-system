package com.osmshop.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailChangeController {
    private final EmailChangeService changes;

    public EmailChangeController(EmailChangeService changes) { this.changes = changes; }

    @PostMapping("/api/me/email-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@AuthenticationPrincipal AuthPrincipal user,
                        @Valid @RequestBody ChangeRequest request) {
        changes.request(user.id(), request.newEmail(), request.currentPassword());
    }

    @PostMapping("/api/auth/email-change/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody ConfirmRequest request) {
        changes.confirm(request.token());
    }

    public record ChangeRequest(@NotBlank @Email String newEmail, @NotBlank String currentPassword) {}
    public record ConfirmRequest(@NotBlank String token) {}
}
