package org.gp.newspinbe.domain.simulation.controller;

import java.util.List;

import org.gp.newspinbe.domain.simulation.dto.request.SessionCreateRequest;
import org.gp.newspinbe.domain.simulation.dto.response.SessionResponse;
import org.gp.newspinbe.domain.simulation.service.SimulationSessionService;
import org.gp.newspinbe.global.common.ApiResponse;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/simulation/sessions")
@RequiredArgsConstructor
@Slf4j
public class SimulationSessionController {

    private final SimulationSessionService sessionService;

    @PostMapping
    public ResponseEntity<ApiResponse<SessionResponse>> createSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Validated @RequestBody SessionCreateRequest request) {
        SessionResponse response = sessionService.createSession(
                userDetails.getUser().getUserId(),
                request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getMySessionList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<SessionResponse> response = sessionService.getMySessionList(
                userDetails.getUser().getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<SessionResponse>> getSessionDetail(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SessionResponse response = sessionService.getSessionDetail(
                sessionId,
                userDetails.getUser().getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        sessionService.deleteSession(
                sessionId,
                userDetails.getUser().getUserId());

        return ResponseEntity.ok(ApiResponse.success());
    }
}
