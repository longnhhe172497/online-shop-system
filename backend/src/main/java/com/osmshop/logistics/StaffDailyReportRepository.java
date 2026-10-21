package com.osmshop.logistics;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffDailyReportRepository extends JpaRepository<StaffDailyReport, Long> {
    Optional<StaffDailyReport> findByStaffIdAndReportDate(Long staffId, LocalDate reportDate);
}
