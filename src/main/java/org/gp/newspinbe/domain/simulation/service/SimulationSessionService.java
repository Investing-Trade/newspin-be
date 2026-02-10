package org.gp.newspinbe.domain.simulation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.gp.newspinbe.domain.simulation.domain.AssetHistory;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.dto.request.SessionCreateRequest;
import org.gp.newspinbe.domain.simulation.dto.response.SessionResponse;
import org.gp.newspinbe.domain.simulation.repository.AssetHistoryRepository;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SimulationSessionService {

    private final SimulationSessionRepository sessionRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public SessionResponse createSession(Long userId, SessionCreateRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 세션 생성
        SimulationSession session = SimulationSession.createSession(
                user,
                request.getInitialCapital(),
                request.getStartDate(),
                request.getEndDate());

        sessionRepository.save(session);

        // 초기 자산 히스토리 생성
        AssetHistory initialHistory = AssetHistory.createHistory(
                session,
                request.getStartDate(),
                request.getInitialCapital(),
                BigDecimal.ZERO);
        assetHistoryRepository.save(initialHistory);

        return SessionResponse.from(session);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate) || startDate.isEqual(endDate)) {
            throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
        }
    }

    public List<SessionResponse> getMySessionList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return sessionRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(SessionResponse::from)
                .collect(Collectors.toList());
    }

    public SessionResponse getSessionDetail(Long sessionId, Long userId) {
        SimulationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        return SessionResponse.from(session);
    }
}
