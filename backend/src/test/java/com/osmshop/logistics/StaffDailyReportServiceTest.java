package com.osmshop.logistics;

import com.osmshop.auth.AuthApiException;
import com.osmshop.logistics.LogisticsDtos.DailyReportView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffDailyReportServiceTest {

    @Mock private StaffDailyReportRepository repository;
    @Mock private JdbcTemplate jdbc;
    @InjectMocks private StaffDailyReportServiceImpl service;

    // UTCID01: submit_whenContentEmpty_throwBadRequest
    @Test
    @DisplayName("UTCID01 - Báo cáo rỗng ném lỗi 400 REPORT_EMPTY")
    void submit_whenContentEmpty_throwBadRequest() {
        assertThatThrownBy(() -> service.submit(102L, "   "))
                .isInstanceOf(AuthApiException.class)
                .hasMessageContaining("Nội dung báo cáo không được để trống.");

        verify(repository, never()).saveAndFlush(any());
    }

    // UTCID02: submit_whenValidContent_saveAndReturnView
    @Test
    @DisplayName("UTCID02 - Báo cáo hợp lệ lưu thành công và trả về view")
    void submit_whenValidContent_saveAndReturnView() {
        LocalDate today = LocalDate.now();
        when(jdbc.queryForObject(eq("SELECT CURRENT_DATE"), any(RowMapper.class))).thenReturn(today);
        when(repository.findByStaffIdAndReportDate(102L, today)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        DailyReportView mockView = new DailyReportView(1L, "Trần Trang (Kho)", today, "Today", "Đã kiểm kê 4 mẫu quần áo", null, true);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(), any())).thenReturn(mockView);

        DailyReportView result = service.submit(102L, "Đã kiểm kê 4 mẫu quần áo");

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("Đã kiểm kê 4 mẫu quần áo");
        verify(repository).saveAndFlush(any(StaffDailyReport.class));
    }

    // UTCID03: submit_whenReportExistsToday_updateContent
    @Test
    @DisplayName("UTCID03 - Cập nhật nội dung nếu đã có báo cáo trong ngày")
    void submit_whenReportExistsToday_updateContent() {
        LocalDate today = LocalDate.now();
        StaffDailyReport existing = new StaffDailyReport();
        existing.setContent("Nội dung cũ ca sáng");

        when(jdbc.queryForObject(eq("SELECT CURRENT_DATE"), any(RowMapper.class))).thenReturn(today);
        when(repository.findByStaffIdAndReportDate(102L, today)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submit(102L, "Nội dung mới cập nhật ca chiều");

        assertThat(existing.getContent()).isEqualTo("Nội dung mới cập nhật ca chiều");
        verify(repository).saveAndFlush(existing);
    }
}