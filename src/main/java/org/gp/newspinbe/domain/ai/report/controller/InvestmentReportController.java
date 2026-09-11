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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "투자 리포트", description = "세션 종료 후 AI 투자 피드백 리포트. Gemini 호출은 비동기로 처리된다(I-11).")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/simulation/sessions")
@RequiredArgsConstructor
public class InvestmentReportController {

    private final InvestmentReportService reportService;

    @Operation(
            summary = "투자 리포트 조회 (비동기 생성, 폴링)",
            description = "규칙 기반 요약(자산/수익률/거래 수)은 즉시 계산해 반환한다. "
                    + "AI 분석 4개 섹션은 최초 요청 시 백그라운드로 생성이 시작되며, 응답의 `status` 로 진행 상태를 알린다: "
                    + "`GENERATING`(HTTP 202, 분석 미완료·재요청 필요) / `READY`(HTTP 200, 4개 섹션 포함) / `FAILED`(HTTP 202, 다음 요청 시 자동 재시도). "
                    + "FE 는 202 응답을 폴링해야 한다.")
    @GetMapping("/{sessionId}/report")
    public ResponseEntity<ApiResponse<InvestmentReportResponse>> getReport(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        InvestmentReportResponse response = reportService.getReport(
                sessionId,
                userDetails.getUser().getUserId());
        // AI 분석이 아직이면 202, 완료면 200 (FE 는 status 필드로 폴링)
        var status = "READY".equals(response.getStatus())
                ? org.springframework.http.HttpStatus.OK
                : org.springframework.http.HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }
}
