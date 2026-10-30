package com.osmshop.catalog;

import com.osmshop.auth.AuthPrincipal;
import com.osmshop.catalog.ManagerCatalogDtos.CategoryRequest;
import com.osmshop.catalog.ManagerCatalogDtos.CategoryStatusRequest;
import com.osmshop.catalog.ManagerCatalogDtos.ManagerCategoryView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Requires the MANAGER_CATEGORY_MANAGE permission (see SecurityConfig). */
@Validated
@RestController
@RequestMapping("/api/manager/categories")
public class ManagerCategoryController {
    private final ManagerCategoryService categories;

    public ManagerCategoryController(ManagerCategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<ManagerCategoryView> list(
            @RequestParam(required = false) @Pattern(regexp = ManagerCatalogDtos.CATEGORY_STATUSES) String status) {
        return categories.list(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagerCategoryView create(@AuthenticationPrincipal AuthPrincipal user,
                                      @Valid @RequestBody CategoryRequest request) {
        return categories.create(user.id(), request);
    }

    @PatchMapping("/{id}")
    public ManagerCategoryView update(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                      @Valid @RequestBody CategoryRequest request) {
        return categories.update(user.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public ManagerCategoryView changeStatus(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                            @Valid @RequestBody CategoryStatusRequest request) {
        return categories.changeStatus(user.id(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id) {
        categories.delete(user.id(), id);
    }
}
