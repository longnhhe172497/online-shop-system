package com.osmshop.auth;

import com.osmshop.auth.ProfileDtos.AddressRequest;
import com.osmshop.auth.ProfileDtos.AddressResponse;
import com.osmshop.auth.ProfileDtos.ProfileResponse;
import com.osmshop.auth.ProfileDtos.ProfileUpdateRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import com.osmshop.auth.AuthDtos.ChangePasswordRequest;
import com.osmshop.auth.AuthDtos.MfaDisableRequest;
import com.osmshop.auth.AuthDtos.MfaVerifyRequest;
import com.osmshop.auth.AuthDtos.SessionView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class ProfileController {
    private final ProfileService profiles;
    private final AuthService auth;

    public ProfileController(ProfileService profiles, AuthService auth) {
        this.profiles = profiles;
        this.auth = auth;
    }

    @GetMapping
    public ProfileResponse me(@AuthenticationPrincipal AuthPrincipal user) {
        return profiles.getProfile(user.id());
    }

    @PatchMapping
    public ProfileResponse update(@AuthenticationPrincipal AuthPrincipal user,
                                  @Valid @RequestBody ProfileUpdateRequest request) {
        return profiles.updateProfile(user.id(), request);
    }

    @GetMapping("/addresses")
    public List<AddressResponse> addresses(@AuthenticationPrincipal AuthPrincipal user) {
        return profiles.listAddresses(user.id());
    }

    @GetMapping("/limits")
    public Map<String, Integer> limits() {
        return Map.of("maxSavedAddresses", profiles.maxSavedAddresses());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse createAddress(@AuthenticationPrincipal AuthPrincipal user,
                                         @Valid @RequestBody AddressRequest request) {
        return profiles.createAddress(user.id(), request);
    }

    @PatchMapping("/addresses/{id}")
    public AddressResponse updateAddress(@AuthenticationPrincipal AuthPrincipal user,
                                         @PathVariable long id,
                                         @Valid @RequestBody AddressRequest request) {
        return profiles.updateAddress(user.id(), id, request);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id) {
        profiles.deleteAddress(user.id(), id);
    }

    @GetMapping("/security")
    public Map<String, Boolean> security(@AuthenticationPrincipal AuthPrincipal user) {
        return Map.of("mfaEnabled", auth.mfaEnabled(user.id()));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthPrincipal user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(user.id(), request.currentPassword(), request.newPassword(), request.confirmPassword());
    }

    @GetMapping("/sessions")
    public List<SessionView> sessions(@AuthenticationPrincipal AuthPrincipal user, HttpServletRequest request) {
        return auth.sessions(user.id(), AuthController.sessionToken(request));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id) {
        auth.revokeSession(user.id(), id);
    }

    @PostMapping("/sessions/revoke-other")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOtherSessions(@AuthenticationPrincipal AuthPrincipal user, HttpServletRequest request) {
        auth.revokeOtherSessions(user.id(), AuthController.sessionToken(request));
    }

    @PostMapping("/mfa/start")
    public Map<String, String> startMfa(@AuthenticationPrincipal AuthPrincipal user) {
        return Map.of("challengeToken", auth.startMfaEnrollment(user.id()));
    }

    @PostMapping("/mfa/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmMfa(@AuthenticationPrincipal AuthPrincipal user,
                           @Valid @RequestBody MfaVerifyRequest request) {
        auth.finishMfaEnrollment(user.id(), request.challengeToken(), request.code());
    }

    @PostMapping("/mfa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableMfa(@AuthenticationPrincipal AuthPrincipal user,
                           @Valid @RequestBody MfaDisableRequest request) {
        auth.disableMfa(user.id(), request.password());
    }
}
