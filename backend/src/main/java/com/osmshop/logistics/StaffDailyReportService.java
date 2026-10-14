package com.osmshop.logistics;

import com.osmshop.logistics.LogisticsDtos.DailyReportView;
import java.util.List;

public interface StaffDailyReportService {
    /** Upsert báo cáo của nhân viên cho ngày hôm nay (report_date = CURRENT_DATE). */
    DailyReportView submit(long staffId, String content);

    /**
     * 10 báo cáo gần nhất: của nhân viên thuộc vai trò teamRole (WAREHOUSE / DELIVERY)
     * và của chính người gọi (để ADMIN cũng thấy báo cáo vừa nộp).
     */
    List<DailyReportView> recent(long staffId, String teamRole);
}
