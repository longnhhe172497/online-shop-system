package com.osmshop.reporting;

import com.osmshop.auth.AuthApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReportingServiceValidationTest {
    @Mock JdbcTemplate jdbc;
    @InjectMocks ReportingService service;

    @Test
    void summary_rejectsReversedDateRangeBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.summary(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-01")))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Khoảng thời gian báo cáo không hợp lệ");
        verifyNoInteractions(jdbc);
    }

    @Test
    void summary_rejectsRangeLongerThan366DaysBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.summary(LocalDate.parse("2025-01-01"), LocalDate.parse("2026-11-01")))
                .isInstanceOf(AuthApiException.class);
        verifyNoInteractions(jdbc);
    }
}
