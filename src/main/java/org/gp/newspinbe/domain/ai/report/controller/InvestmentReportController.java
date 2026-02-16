package org.gp.newspinbe.domain.ai.report.controller;

import org.gp.newspinbe.domain.ai.report.dto.response.InvestmentReportResponse;
import org.gp.newspinbe.domain.ai.report.service.InvestmentReportService;
import org.gp.newspinbe.global.common.ApiResponse;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/simulation/sessions")
@RequiredArgsConstructor
public class InvestmentReportController {

    private final InvestmentReportService reportService;

    @GetMapping("/{sessionId}/report")
    public ResponseEntity<ApiResponse<InvestmentReportResponse>> generateReport(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        InvestmentReportResponse response = reportService.generateReport(
                sessionId,
                userDetails.getUser().getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
