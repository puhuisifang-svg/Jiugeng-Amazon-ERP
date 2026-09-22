package com.jiugeng.erp.mockdata;

import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("mock")
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
class MockDashboardController {

    @GetMapping("/summary")
    DashboardSummary summary() {
        return new DashboardSummary(
                true,
                "SYNTHETIC",
                Instant.parse("2026-09-23T00:00:00Z"),
                List.of(
                        new SummaryItem("待处理导入", 3, "模拟批次，不进入正式台账"),
                        new SummaryItem("库存预警", 5, "模拟单仓库存"),
                        new SummaryItem("待复核新品", 8, "模拟公开店铺快照")));
    }

    record DashboardSummary(
            boolean mockData,
            String source,
            Instant generatedAt,
            List<SummaryItem> items) {
    }

    record SummaryItem(String label, int value, String note) {
    }
}
