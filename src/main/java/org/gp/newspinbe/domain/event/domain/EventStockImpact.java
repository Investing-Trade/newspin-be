package org.gp.newspinbe.domain.event.domain;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
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
 * 이벤트 뉴스가 특정 종목에 미치는 영향도
 * 중요: 사용자에게는 절대 노출하지 않음 (AI 보고서 생성 시에만 사용하는 정답 데이터)
 * NewsArticle(eventType != null)과 연결됨
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "event_stock_impact", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "news_id", "stock_id" })
}, indexes = {
        @Index(name = "idx_impact_news", columnList = "news_id")
})
public class EventStockImpact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long impactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private NewsArticle newsArticle; // 이벤트 뉴스와 연결

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private Double impactRate; // 실제 영향도 (%)

    @Column(columnDefinition = "TEXT")
    private String impactReason; // 영향도 이유 설명

    private EventStockImpact(NewsArticle newsArticle, Stock stock, Double impactRate, String impactReason) {
        this.newsArticle = newsArticle;
        this.stock = stock;
        this.impactRate = impactRate;
        this.impactReason = impactReason;
    }

    public static EventStockImpact createImpact(NewsArticle newsArticle, Stock stock,
            Double impactRate, String impactReason) {
        return new EventStockImpact(newsArticle, stock, impactRate, impactReason);
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
