package org.gp.newspinbe.domain.simulation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.gp.newspinbe.domain.simulation.domain.Trade;
import org.gp.newspinbe.domain.simulation.domain.TradeType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TradeResponse {

    private Long tradeId;
    private Long sessionId;
    private String stockCode;
    private String stockName;
    private TradeType tradeType;
    private Long quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private LocalDate tradeDate;
    private BigDecimal currentCapital; // 거래 후 잔고
    private LocalDateTime createdAt;

    public static TradeResponse from(Trade trade, BigDecimal currentCapital) {
        return TradeResponse.builder()
                .tradeId(trade.getTradeId())
                .sessionId(trade.getSession().getSessionId())
                .stockCode(trade.getStock().getStockCode())
                .stockName(trade.getStock().getStockName())
                .tradeType(trade.getTradeType())
                .quantity(trade.getQuantity())
                .price(trade.getPrice())
                .totalAmount(trade.getTotalAmount())
                .tradeDate(trade.getTradeDate())
                .currentCapital(currentCapital)
                .createdAt(trade.getCreatedAt())
                .build();
    }
}
