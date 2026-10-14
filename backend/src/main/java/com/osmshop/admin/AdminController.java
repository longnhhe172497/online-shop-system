package com.osmshop.admin;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) { this.service = service; }

    @GetMapping("/users")
    public PageResponse<AdminService.UserView> users(@RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String role, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.users(search, role, status, page, size);
    }

    @GetMapping("/users/{id}")
    public AdminService.UserDetailView user(@PathVariable long id) { return service.userDetail(id); }

    @PostMapping("/users/{id}/sessions/revoke")
    public void revokeSessions(@AuthenticationPrincipal AuthPrincipal actor, @PathVariable long id) {
        service.revokeSessions(actor.id(), id);
    }

    @PatchMapping("/users/{id}")
    public AdminService.UserView update(@AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable long id, @Valid @RequestBody UpdateUser request) {
        return service.update(actor.id(), id, request);
    }

    @PatchMapping("/users/{id}/role")
    public AdminService.UserView role(@AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable long id, @Valid @RequestBody RoleChange request) {
        return service.changeRole(actor.id(), id, request.role());
    }

    @PatchMapping("/users/{id}/status")
    public AdminService.UserView status(@AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable long id, @Valid @RequestBody StatusChange request) {
        return service.changeStatus(actor.id(), id, request.status());
    }

    @GetMapping("/settings")
    public java.util.List<AdminService.SettingView> settings() { return service.settings(); }

    @PatchMapping("/settings/{key}")
    public AdminService.SettingView setting(@AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable String key, @Valid @RequestBody SettingChange request) {
        return service.changeSetting(actor.id(), key, request.value());
    }

    @GetMapping("/audit-logs")
    public PageResponse<AdminService.AuditView> audit(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String actor,
            @RequestParam(defaultValue = "") String action,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return service.audit(page, size, actor, action, from, to);
    }

    public record UpdateUser(@NotBlank @Size(max = 150) String fullName, @Size(max = 30) String phone) {}
    public record RoleChange(@NotBlank String role) {}
    public record StatusChange(@NotBlank String status) {}
    public record SettingChange(@NotBlank String value) {}
}
