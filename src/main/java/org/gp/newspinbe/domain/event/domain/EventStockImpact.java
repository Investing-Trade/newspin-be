package org.gp.newspinbe.domain.event.domain;

import org.gp.newspinbe.domain.stock.domain.Stock;

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

/**
 * 이벤트가 특정 종목에 미치는 영향도
 * 중요: 사용자에게는 절대 노출하지 않음 (AI 보고서 생성 시에만 사용하는 정답 데이터)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event_stock_impact", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "event_id", "stock_id" })
}, indexes = {
        @Index(name = "idx_impact_event", columnList = "event_id")
})
public class EventStockImpact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long impactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private MarketEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    // 실제 영향도
    @Column(nullable = false)
    private Double impactRate;

    // 영향도 이유 설명
    @Column(columnDefinition = "TEXT")
    private String impactReason;

    private EventStockImpact(MarketEvent event, Stock stock, Double impactRate, String impactReason) {
        this.event = event;
        this.stock = stock;
        this.impactRate = impactRate;
        this.impactReason = impactReason;
    }

    public static EventStockImpact createImpact(MarketEvent event, Stock stock,
            Double impactRate, String impactReason) {
        return new EventStockImpact(event, stock, impactRate, impactReason);
    }

    public void updateImpact(Double impactRate, String impactReason) {
        this.impactRate = impactRate;
        this.impactReason = impactReason;
    }

    // 영향도가 유의미한 수준인지 확인 (절대값 5% 이상)
    public boolean isSignificant() {
        return Math.abs(impactRate) >= 5.0;
    }

    // 긍정적 영향인지 확인
    public boolean isPositive() {
        return impactRate > 0;
    }
}
