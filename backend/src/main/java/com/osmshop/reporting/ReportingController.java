package com.osmshop.reporting;

import com.osmshop.support.AfterSalesDtos.ReportSummary;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/reports")
public class ReportingController {
    private final ReportingService reports;
    public ReportingController(ReportingService reports){this.reports=reports;}

    @GetMapping("/summary")
    public ReportSummary summary(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return reports.summary(from,to);}

    @GetMapping(value="/export.csv",produces="text/csv")
    public ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        ReportSummary data=reports.summary(from,to);
        StringBuilder csv=new StringBuilder("\uFEFFdate,order_count,paid_order_count,gross_revenue_vnd,refunded_amount_vnd,refund_count\r\n");
        for(var d:data.daily()) csv.append(d.date()).append(',').append(d.orderCount()).append(',').append(d.paidOrderCount())
                .append(',').append(d.grossRevenue()).append(',').append(d.refundedAmount()).append(',').append(d.refundCount()).append("\r\n");
        csv.append("TOTAL,").append(data.orderCount()).append(',').append(data.paidOrderCount()).append(',')
                .append(data.grossRevenue()).append(',').append(data.refundedAmount()).append(',').append(data.refundCount()).append("\r\n");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=transaction-report-"+from+"-"+to+".csv")
                .contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
