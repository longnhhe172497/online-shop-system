package com.osmshop.logistics;

import com.osmshop.auth.AuthApiException;
import com.osmshop.logistics.LogisticsDtos.DailyReportView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffDailyReportServiceImpl implements StaffDailyReportService {
    private static final int RECENT_LIMIT = 10;
    /** Khóa advisory theo nhân viên để hai lần bấm gửi đồng thời không tạo trùng bản ghi. */
    private static final long LOCK_BASE = 780_000_000_000L;
    private static final DateTimeFormatter OLDER_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SELECT = """
            SELECT r.id,u.full_name,r.report_date,r.content,r.updated_at,(r.staff_id = ?),CURRENT_DATE
            FROM staff_daily_reports r JOIN users u ON u.id = r.staff_id""";

    private final StaffDailyReportRepository reports;
    private final JdbcTemplate jdbc;

    public StaffDailyReportServiceImpl(StaffDailyReportRepository reports, JdbcTemplate jdbc) {
        this.reports = reports;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public DailyReportView submit(long staffId, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) throw new AuthApiException(HttpStatus.BAD_REQUEST, "REPORT_EMPTY",
                "Nội dung báo cáo không được để trống.");
        jdbc.execute("SELECT pg_advisory_xact_lock(" + (LOCK_BASE + staffId) + ")");
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", (rs, row) -> rs.getObject(1, LocalDate.class));
        StaffDailyReport report = reports.findByStaffIdAndReportDate(staffId, today).orElseGet(() -> {
            StaffDailyReport created = new StaffDailyReport();
            created.setStaffId(staffId);
            created.setReportDate(today);
            return created;
        });
        report.setContent(text);
        report = reports.saveAndFlush(report);
        return jdbc.queryForObject(SELECT + " WHERE r.id = ?", StaffDailyReportServiceImpl::map, staffId, report.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyReportView> recent(long staffId, String teamRole) {
        return jdbc.query(SELECT + """
                 WHERE u.role = ? OR r.staff_id = ?
                ORDER BY r.report_date DESC, r.updated_at DESC, r.id DESC LIMIT ?""",
                StaffDailyReportServiceImpl::map, staffId, teamRole, staffId, RECENT_LIMIT);
    }

    private static DailyReportView map(ResultSet rs, int row) throws SQLException {
        LocalDate date = rs.getObject(3, LocalDate.class);
        LocalDate today = rs.getObject(7, LocalDate.class);
        String timeAgo = date.equals(today) ? "Today"
                : date.equals(today.minusDays(1)) ? "Yesterday" : date.format(OLDER_FORMAT);
        return new DailyReportView(rs.getLong(1), rs.getString(2), date, timeAgo, rs.getString(4),
                rs.getObject(5, OffsetDateTime.class), rs.getBoolean(6));
    }
}
