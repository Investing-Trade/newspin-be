package org.gp.newspinbe.domain.simulation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 자산 변동 추적(매일 종료 시점의 총자산을 기록하여 시계열 데이터로 활용)

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "asset_history", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "session_id", "record_date" })
}, indexes = {
        @Index(name = "idx_asset_session", columnList = "session_id"),
        @Index(name = "idx_asset_date", columnList = "record_date")
})
public class AssetHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SimulationSession session;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate; // 기록 날짜

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal cashBalance; // 현금 잔고

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal stockValue; // 주식 평가액

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAsset; // 총 자산 (현금 + 주식)

    @Column(nullable = false, precision = 7, scale = 4)
    private Double profitRate; // 수익률 (%)

    private AssetHistory(SimulationSession session, LocalDate recordDate,
            BigDecimal cashBalance, BigDecimal stockValue) {
        this.session = session;
        this.recordDate = recordDate;
        this.cashBalance = cashBalance;
        this.stockValue = stockValue;
        this.totalAsset = cashBalance.add(stockValue);
        this.profitRate = calculateProfitRate(session.getInitialCapital(), totalAsset);
    }

    public static AssetHistory createHistory(SimulationSession session, LocalDate recordDate,
            BigDecimal cashBalance, BigDecimal stockValue) {
        return new AssetHistory(session, recordDate, cashBalance, stockValue);
    }

    // 수익률 계산
    private static Double calculateProfitRate(BigDecimal initialCapital, BigDecimal totalAsset) {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return totalAsset.subtract(initialCapital)
                .divide(initialCapital, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    // 수익금 계산
    public BigDecimal calculateProfit() {
        return totalAsset.subtract(session.getInitialCapital());
    }

    // 전일 대비 변동률 계산
    public Double calculateDailyChange(BigDecimal previousTotalAsset) {
        if (previousTotalAsset.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return totalAsset.subtract(previousTotalAsset)
                .divide(previousTotalAsset, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
