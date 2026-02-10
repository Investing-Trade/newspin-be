package org.gp.newspinbe.domain.simulation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.gp.newspinbe.domain.news.dto.response.NewsResponse;
import org.gp.newspinbe.domain.simulation.domain.AssetHistory;
import org.gp.newspinbe.domain.simulation.domain.SessionStatus;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DayResponse {

    private Long sessionId;
    private LocalDate simulationDate; // 진행된 날짜
    private BigDecimal currentCapital; // 현금 잔고
    private BigDecimal totalAsset; // 총 자산 (현금 + 주식 평가액)
    private Double profitRate; // 누적 수익률 (%)
    private Double dailyProfitRate; // 전일 대비 수익률 (%)
    private SessionStatus status; // 세션 상태

    private List<NewsResponse> todayNews; // 오늘 발생한 뉴스 리스트

    public static DayResponse from(SimulationSession session, AssetHistory todayHistory,
            AssetHistory yesterdayHistory, List<NewsResponse> newsList) {

        Double dailyProfitRate = 0.0;
        if (yesterdayHistory != null && yesterdayHistory.getTotalAsset().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = todayHistory.getTotalAsset().subtract(yesterdayHistory.getTotalAsset());
            dailyProfitRate = diff.divide(yesterdayHistory.getTotalAsset(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        return DayResponse.builder()
                .sessionId(session.getSessionId())
                .simulationDate(session.getCurrentSimulationDate())
                .currentCapital(session.getCurrentCapital())
                .totalAsset(todayHistory.getTotalAsset())
                .profitRate(todayHistory.getProfitRate())
                .dailyProfitRate(dailyProfitRate)
                .status(session.getStatus())
                .todayNews(newsList)
                .build();
    }
}
