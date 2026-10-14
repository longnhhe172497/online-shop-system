package com.osmshop.admin;

import com.osmshop.auth.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/role-permissions")
public class RolePermissionController {
    private final RolePermissionService service;
    public RolePermissionController(RolePermissionService service) { this.service = service; }

    @GetMapping
    public RolePermissionService.RolePermissionMatrix matrix() { return service.matrix(); }

    @PutMapping("/{role}")
    public RolePermissionService.RoleGrant replace(@AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable String role, @Valid @RequestBody RolePermissionService.UpdatePermissions request) {
        return service.replace(actor.id(), role, request);
    }
}
