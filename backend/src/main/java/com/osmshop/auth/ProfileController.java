package com.osmshop.auth;

import com.osmshop.auth.ProfileDtos.AddressRequest;
import com.osmshop.auth.ProfileDtos.AddressResponse;
import com.osmshop.auth.ProfileDtos.ProfileResponse;
import com.osmshop.auth.ProfileDtos.ProfileUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
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

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
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
}
