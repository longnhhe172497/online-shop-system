package com.osmshop.order;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthPrincipal;
import com.osmshop.order.VoucherService.VoucherRequest;
import com.osmshop.order.VoucherService.VoucherView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/** Requires the MANAGER_VOUCHER_MANAGE permission (see SecurityConfig). */
@Validated
@RestController
@RequestMapping("/api/manager/vouchers")
public class VoucherController {
    private final VoucherService vouchers;

    public VoucherController(VoucherService vouchers) {
        this.vouchers = vouchers;
    }

    @GetMapping
    public PageResponse<VoucherView> list(@RequestParam(required = false) String search,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return vouchers.list(search, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VoucherView create(@AuthenticationPrincipal AuthPrincipal user, @Valid @RequestBody VoucherRequest request) {
        return vouchers.create(user.id(), request);
    }

    @PatchMapping("/{id}")
    public VoucherView update(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id,
                              @Valid @RequestBody VoucherRequest request) {
        return vouchers.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal user, @PathVariable long id) {
        vouchers.delete(user.id(), id);
    }
}
