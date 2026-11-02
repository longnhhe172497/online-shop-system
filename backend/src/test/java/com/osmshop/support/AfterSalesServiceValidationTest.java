package com.osmshop.support;

import com.osmshop.auth.AuthApiException;
import com.osmshop.support.AfterSalesDtos.CreateReturnRequest;
import com.osmshop.support.AfterSalesDtos.ReturnItemRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AfterSalesServiceValidationTest {
    @Mock JdbcTemplate jdbc;
    @InjectMocks AfterSalesService service;

    @Test
    void createReturn_rejectsUnknownRequestTypeBeforeDatabaseAccess() {
        var request = new CreateReturnRequest("INVALID", "Not a supported return type", null,
                List.of(new ReturnItemRequest(10L, 1)));
        assertThatThrownBy(() -> service.createReturn(5L, 20L, request))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Loại yêu cầu trả hàng không hợp lệ");
        verifyNoInteractions(jdbc);
    }
}
