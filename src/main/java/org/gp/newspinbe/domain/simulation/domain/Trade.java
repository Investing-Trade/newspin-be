package org.gp.newspinbe.domain.simulation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.gp.newspinbe.domain.stock.domain.Stock;
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

//거래 내역 (매수/매도)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "trade", indexes = {
        @Index(name = "idx_trade_session", columnList = "session_id"),
        @Index(name = "idx_trade_date", columnList = "trade_date"),
        @Index(name = "idx_trade_session_date", columnList = "session_id, trade_date")
})
public class Trade extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SimulationSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeType tradeType;

    @Column(nullable = false)
    private Integer quantity; // 거래 수량

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price; // 거래 가격 (주당)

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount; // 총 거래 금액 (price * quantity)

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate; // 거래 날짜

    private Trade(SimulationSession session, Stock stock, TradeType tradeType,
            Integer quantity, BigDecimal price, LocalDate tradeDate) {
        this.session = session;
        this.stock = stock;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.price = price;
        this.totalAmount = price.multiply(BigDecimal.valueOf(quantity));
        this.tradeDate = tradeDate;
    }

    public static Trade createTrade(SimulationSession session, Stock stock, TradeType tradeType,
            Integer quantity, BigDecimal price, LocalDate tradeDate) {
        validateQuantity(quantity);
        validatePrice(price);
        return new Trade(session, stock, tradeType, quantity, price, tradeDate);
    }

    public boolean isBuy() {
        return tradeType == TradeType.BUY;
    }

    public boolean isSell() {
        return tradeType == TradeType.SELL;
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다.");
        }
    }

    private static void validatePrice(BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다.");
        }
    }
}
