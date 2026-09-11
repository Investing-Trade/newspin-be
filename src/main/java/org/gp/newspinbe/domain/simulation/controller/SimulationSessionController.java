package org.gp.newspinbe.domain.simulation.controller;


import org.gp.newspinbe.domain.simulation.dto.request.SessionCreateRequest;
import org.gp.newspinbe.domain.simulation.dto.response.DayResponse;
import org.gp.newspinbe.domain.simulation.dto.response.SessionResponse;
import org.gp.newspinbe.domain.simulation.service.NextDayService;
import org.gp.newspinbe.domain.simulation.service.SimulationSessionService;
import org.gp.newspinbe.global.common.ApiResponse;
import org.gp.newspinbe.global.common.PageResponse;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "모의투자 세션", description = "세션 생성/진행/조회. 시세는 세션의 currentSimulationDate 를 넘어서 조회되지 않는다(lookahead 차단).")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/simulation/sessions")
@RequiredArgsConstructor
@Slf4j
public class SimulationSessionController {

        private final SimulationSessionService sessionService;
        private final NextDayService nextDayService;
        private final org.gp.newspinbe.domain.simulation.service.PortfolioService portfolioService;

        @Operation(summary = "세션 생성", description = "초기 자본과 시작일을 지정해 모의투자 세션을 시작한다.")
        @PostMapping
        public ResponseEntity<ApiResponse<SessionResponse>> createSession(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @Validated @RequestBody SessionCreateRequest request) {
                SessionResponse response = sessionService.createSession(
                                userDetails.getUser().getUserId(),
                                request);

                return ResponseEntity.ok(ApiResponse.success(response));
        }

        @Operation(summary = "다음 거래일로 진행", description = "세션을 하루 진행시키고 자산 스냅샷을 저장한다(AssetHistory).")
        @PostMapping("/{sessionId}/next-day")
        public ResponseEntity<ApiResponse<DayResponse>> proceedToNextDay(
                        @PathVariable Long sessionId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                DayResponse response = nextDayService.proceedToNextDay(
                                sessionId,
                                userDetails.getUser().getUserId());

                return ResponseEntity.ok(ApiResponse.success(response));
        }

        @Operation(summary = "현재일 데이터 조회", description = "저장 없이 세션의 현재 거래일 데이터를 다시 조회한다.")
        @GetMapping("/{sessionId}/daily-data")
        public ResponseEntity<ApiResponse<DayResponse>> getDailyData(
                        @PathVariable Long sessionId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                DayResponse response = nextDayService.getCurrentDayData(
                                sessionId,
                                userDetails.getUser().getUserId());

                return ResponseEntity.ok(ApiResponse.success(response));
        }

        @Operation(summary = "포트폴리오 개요 조회", description = "세션의 보유 종목, 평가금액, 수익률 등 현재 포트폴리오 요약.")
        @GetMapping("/{sessionId}/portfolio")
        public ResponseEntity<ApiResponse<org.gp.newspinbe.domain.simulation.dto.response.PortfolioOverviewResponse>> getPortfolioOverview(
                        @PathVariable Long sessionId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                org.gp.newspinbe.domain.simulation.dto.response.PortfolioOverviewResponse response = portfolioService
                                .getPortfolioOverview(
                                                sessionId,
                                                userDetails.getUser().getUserId());

                return ResponseEntity.ok(ApiResponse.success(response));
        }

        @Operation(summary = "내 세션 목록 조회 (페이지네이션)", description = "기본 page=0, size=20, 최대 size=100(I-10).")
        @GetMapping
        public ResponseEntity<ApiResponse<PageResponse<SessionResponse>>> getMySessionList(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
                PageResponse<SessionResponse> response = PageResponse.from(
                                sessionService.getMySessionList(userDetails.getUser().getUserId(), pageable));

                return ResponseEntity.ok(ApiResponse.success(response));
        }

        @Operation(summary = "세션 상세 조회")
        @GetMapping("/{sessionId}")
        public ResponseEntity<ApiResponse<SessionResponse>> getSessionDetail(
                        @PathVariable Long sessionId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                SessionResponse response = sessionService.getSessionDetail(
                                sessionId,
                                userDetails.getUser().getUserId());

                return ResponseEntity.ok(ApiResponse.success(response));
        }

        @Operation(summary = "세션 포기", description = "세션을 ABANDONED 상태로 전환한다(물리 삭제 아님).")
        @DeleteMapping("/{sessionId}")
        public ResponseEntity<ApiResponse<Void>> deleteSession(
                        @PathVariable Long sessionId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                sessionService.deleteSession(
                                sessionId,
                                userDetails.getUser().getUserId());

                return ResponseEntity.ok(ApiResponse.success());
        }

        @Operation(summary = "세션 종료", description = "세션을 COMPLETED 상태로 전환한다. 종료 후 투자 리포트(GET /{sessionId}/report) 조회가 가능하다.")
        @PutMapping("/{sessionId}/complete")
        public ResponseEntity<ApiResponse<SessionResponse>> completeSession(
                        @PathVariable Long sessionId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                SessionResponse response = sessionService.completeSession(
                                sessionId,
                                userDetails.getUser().getUserId());

                return ResponseEntity.ok(ApiResponse.success(response));
        }
}
