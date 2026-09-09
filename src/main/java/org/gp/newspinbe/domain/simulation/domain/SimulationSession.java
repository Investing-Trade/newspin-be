package org.gp.newspinbe.domain.simulation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

//모의 투자 시뮬레이션 세션(사용자의 투자 시뮬레이션 관리)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "simulation_session", indexes = {
        @Index(name = "idx_session_user", columnList = "user_id"),
        @Index(name = "idx_session_status", columnList = "status")
})
public class SimulationSession extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal initialCapital; // 초기 투자 금액

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal currentCapital; // 현재 보유 현금

    @Column(nullable = false)
    private LocalDate startDate; // 시뮬레이션 시작 날짜

    @Column(nullable = false)
    private LocalDate endDate; // 시뮬레이션 종료 날짜

    @Column(nullable = false)
    private LocalDate currentSimulationDate; // 현재 시뮬레이션이 진행 중인 날짜

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status; // 세션 상태

    private SimulationSession(User user, BigDecimal initialCapital, LocalDate startDate, LocalDate endDate) {
        this.user = user;
        this.initialCapital = initialCapital;
        this.currentCapital = initialCapital; // 처음에는 전액 현금
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentSimulationDate = startDate; // 시작일부터 시작
        this.status = SessionStatus.ACTIVE;
    }

    public static SimulationSession createSession(User user, BigDecimal initialCapital,
            LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        validateCapital(initialCapital);
        return new SimulationSession(user, initialCapital, startDate, endDate);
    }

    // 특정 날짜로 업데이트 (주말 건너뛰기 등). 실제 "다음 날 진행" 로직은 NextDayService 가 담당.
    public void updateCurrentSimulationDate(LocalDate nextDate) {
        if (nextDate.isBefore(this.currentSimulationDate)) {
            throw new IllegalArgumentException("과거 날짜로 돌아갈 수 없습니다.");
        }
        this.currentSimulationDate = nextDate;
    }

    // 거래 시 잔고 조정 (매수)
    public void decreaseCapital(BigDecimal amount) {
        if (currentCapital.compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔고가 부족합니다.");
        }
        this.currentCapital = currentCapital.subtract(amount);
    }

    // 거래 시 잔고 조정 (매도)
    public void increaseCapital(BigDecimal amount) {
        this.currentCapital = currentCapital.add(amount);
    }

    public void complete() {
        this.status = SessionStatus.COMPLETED;
    }

    public void abandon() {
        this.status = SessionStatus.ABANDONED;
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다.");
        }
        if (startDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("시작일은 현재 날짜보다 이전이어야 합니다.");
        }
    }

    private static void validateCapital(BigDecimal capital) {
        if (capital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("초기 자본은 0보다 커야 합니다.");
        }
    }
}
