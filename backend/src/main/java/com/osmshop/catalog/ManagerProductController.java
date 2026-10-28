package com.osmshop.catalog;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.catalog.ManagerCatalogDtos.CategoryOption;
import com.osmshop.catalog.ManagerCatalogDtos.ManagerProductView;
import com.osmshop.catalog.ManagerCatalogDtos.ProductRequest;
import com.osmshop.catalog.ManagerCatalogDtos.ProductStatusRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Requires the MANAGER_PRODUCT_MANAGE permission (see SecurityConfig). */
@Validated
@RestController
@RequestMapping("/api/manager/products")
public class ManagerProductController {
    private final ManagerProductService products;

    public ManagerProductController(ManagerProductService products) {
        this.products = products;
    }

    @GetMapping
    public PageResponse<ManagerProductView> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Pattern(regexp = ManagerCatalogDtos.PRODUCT_STATUSES) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return products.list(q, categoryId, status, page, size);
    }

    /** Category choices for the product form, so this screen does not need the category permission. */
    @GetMapping("/category-options")
    public List<CategoryOption> categoryOptions() {
        return products.categoryOptions();
    }

    @GetMapping("/{id}")
    public ManagerProductView get(@PathVariable long id) {
        return products.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagerProductView create(@AuthenticationPrincipal AuthPrincipal user,
                                     @Valid @RequestBody ProductRequest request) {
        return products.create(user.id(), request);
    }

    @PatchMapping("/{id}")
    public ManagerProductView update(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                     @Valid @RequestBody ProductRequest request) {
        return products.update(user.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public ManagerProductView changeStatus(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                                           @Valid @RequestBody ProductStatusRequest request) {
        return products.changeStatus(user.id(), id, request.status());
    }
}
