package org.gp.newspinbe.domain.simulation.controller;

import org.gp.newspinbe.domain.simulation.dto.request.TradeRequest;
import org.gp.newspinbe.domain.simulation.dto.response.TradeResponse;
import org.gp.newspinbe.domain.simulation.service.TradeService;
import org.gp.newspinbe.global.common.ApiResponse;
import org.gp.newspinbe.global.common.PageResponse;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "거래", description = "세션 내 매수/매도 실행 및 거래 내역 조회")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/simulation/sessions/{sessionId}/trades")
@RequiredArgsConstructor
@Slf4j
public class TradeController {

    private final TradeService tradeService;

    @Operation(summary = "매수/매도 실행", description = "체결가는 서버가 세션 현재일 종가 기준으로 검증한다(요청값을 그대로 신뢰하지 않음, C-3).")
    @PostMapping
    public ResponseEntity<ApiResponse<TradeResponse>> executeTrade(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Validated @RequestBody TradeRequest request) {
        TradeResponse response = tradeService.executeTrade(
                sessionId,
                userDetails.getUser().getUserId(),
                request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "거래 내역 조회 (페이지네이션)", description = "기본 page=0, size=20, 최대 size=100(I-10).")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TradeResponse>>> getTradeHistory(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<TradeResponse> response = PageResponse.from(
                tradeService.getTradeHistory(sessionId, userDetails.getUser().getUserId(), pageable));

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
