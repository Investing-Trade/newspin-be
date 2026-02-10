package org.gp.newspinbe.domain.simulation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.global.common.BaseEntity;

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

//포트폴리오 (보유 종목)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "portfolio", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "session_id", "stock_id" })
}, indexes = {
        @Index(name = "idx_portfolio_session", columnList = "session_id")
})
public class Portfolio extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long portfolioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SimulationSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private Integer quantity; // 보유 수량

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal avgPurchasePrice; // 평균 매입 가격

    private Portfolio(SimulationSession session, Stock stock, Integer quantity, BigDecimal avgPurchasePrice) {
        this.session = session;
        this.stock = stock;
        this.quantity = quantity;
        this.avgPurchasePrice = avgPurchasePrice;
    }

    public static Portfolio createPortfolio(SimulationSession session, Stock stock,
            Integer quantity, BigDecimal purchasePrice) {
        validateQuantity(quantity);
        return new Portfolio(session, stock, quantity, purchasePrice);
    }

    // 매수 시 평균 단가 재계산
    public void addStock(Integer additionalQuantity, BigDecimal purchasePrice) {
        BigDecimal currentTotal = avgPurchasePrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal additionalTotal = purchasePrice.multiply(BigDecimal.valueOf(additionalQuantity));
        Integer newQuantity = quantity + additionalQuantity;

        this.avgPurchasePrice = currentTotal.add(additionalTotal)
                .divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
    }

    // 매도 시 수량 감소
    public void removeStock(Integer soldQuantity) {
        if (soldQuantity > quantity) {
            throw new IllegalArgumentException("보유 수량보다 많이 매도할 수 없습니다.");
        }
        this.quantity -= soldQuantity;
    }

    // 현재가 기준 평가액 계산
    public BigDecimal calculateCurrentValue(BigDecimal currentPrice) {
        return currentPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // 수익률 계산
    public double calculateProfitRate(BigDecimal currentPrice) {
        if (avgPurchasePrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return currentPrice.subtract(avgPurchasePrice)
                .divide(avgPurchasePrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    // 수익금 계산
    public BigDecimal calculateProfit(BigDecimal currentPrice) {
        BigDecimal currentValue = calculateCurrentValue(currentPrice);
        BigDecimal purchaseValue = avgPurchasePrice.multiply(BigDecimal.valueOf(quantity));
        return currentValue.subtract(purchaseValue);
    }

    // 보유 종목이 없는지 확인
    public boolean isEmpty() {
        return quantity == 0;
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("수량은 0 이상이어야 합니다.");
        }
    }
}
