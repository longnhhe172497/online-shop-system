package com.osmshop.admin;

import com.osmshop.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvitationController {
    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) { this.invitations = invitations; }

    @PostMapping("/api/admin/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationView invite(@AuthenticationPrincipal AuthPrincipal admin,
                                 @Valid @RequestBody InviteRequest request) {
        return invitations.invite(admin.id(), request);
    }

    @GetMapping("/api/admin/invitations")
    public List<InvitationView> list() { return invitations.list(); }

    @PostMapping("/api/invitations/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public void accept(@Valid @RequestBody AcceptRequest request) { invitations.accept(request); }

    public record InviteRequest(@NotBlank @Email String email,
                                @NotBlank @Size(max = 150) String fullName,
                                @Size(max = 30) String phone,
                                @NotBlank String role) {}
    public record AcceptRequest(@NotBlank String token,
                                @NotBlank @Size(min = 8, max = 72) String password,
                                @NotBlank String confirmPassword) {}
    public record InvitationView(long id, String email, String fullName, String role,
                                 OffsetDateTime expiresAt, OffsetDateTime acceptedAt,
                                 OffsetDateTime createdAt) {}
}
