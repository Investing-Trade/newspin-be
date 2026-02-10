package org.gp.newspinbe.domain.stock.domain;

import java.math.BigDecimal;
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

// 주가 히스토리 (일별 주가 데이터)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_price", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "stock_id", "price_date" })
}, indexes = {
        @Index(name = "idx_stock_price_date", columnList = "price_date"),
        @Index(name = "idx_stock_price_stock_date", columnList = "stock_id, price_date")
})
public class StockPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long priceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate; // 주가 날짜

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal openPrice; // 시가

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal closePrice; // 종가

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal highPrice; // 고가

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lowPrice; // 저가

    @Column(nullable = false)
    private Long volume; // 거래량

    private StockPrice(Stock stock, LocalDate priceDate, BigDecimal openPrice,
            BigDecimal closePrice, BigDecimal highPrice,
            BigDecimal lowPrice, Long volume) {
        this.stock = stock;
        this.priceDate = priceDate;
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
    }

    public static StockPrice createStockPrice(Stock stock, LocalDate priceDate,
            BigDecimal openPrice, BigDecimal closePrice,
            BigDecimal highPrice, BigDecimal lowPrice,
            Long volume) {
        return new StockPrice(stock, priceDate, openPrice, closePrice,
                highPrice, lowPrice, volume);
    }

    // 전일 대비 변동률 계산
    public double calculateChangeRate(BigDecimal previousClose) {
        if (previousClose == null || previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return closePrice.subtract(previousClose)
                .divide(previousClose, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
