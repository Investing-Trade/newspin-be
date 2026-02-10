package org.gp.newspinbe.domain.simulation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.gp.newspinbe.domain.simulation.domain.SessionStatus;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SessionResponse {

    private Long sessionId;
    private Long userId;
    private BigDecimal initialCapital;
    private BigDecimal currentCapital;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate currentSimulationDate;
    private SessionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SessionResponse from(SimulationSession session) {
        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUser().getUserId())
                .initialCapital(session.getInitialCapital())
                .currentCapital(session.getCurrentCapital())
                .startDate(session.getStartDate())
                .endDate(session.getEndDate())
                .currentSimulationDate(session.getCurrentSimulationDate())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
