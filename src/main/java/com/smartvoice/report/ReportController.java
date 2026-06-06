package com.smartvoice.report;

import com.smartvoice.common.response.ApiResponse;
import com.smartvoice.report.dto.ProgressOverviewResponse;
import com.smartvoice.report.dto.SessionReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/sessions/{sessionId}/report")
    public ResponseEntity<ApiResponse<SessionReportResponse>> generateSessionReport(@PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(SessionReportResponse.from(reportService.generateSessionReport(sessionId))));
    }

    @GetMapping("/sessions/{sessionId}/report")
    public ResponseEntity<ApiResponse<SessionReportResponse>> getSessionReport(@PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(SessionReportResponse.from(reportService.getSessionReport(sessionId))));
    }

    @GetMapping("/progress/overview")
    public ResponseEntity<ApiResponse<ProgressOverviewResponse>> getProgressOverview(
            Authentication authentication,
            @RequestParam(defaultValue = "WEEKLY") String period) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getProgressOverview(authentication.getPrincipal().toString(), period)));
    }
}
