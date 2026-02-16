package org.gp.newspinbe.domain.simulation.dto.response;

import java.math.BigDecimal;
import java.util.List;

import org.gp.newspinbe.domain.simulation.domain.Portfolio;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PortfolioOverviewResponse {

    private BigDecimal currentCapital; // 현재 현금
    private BigDecimal totalStockValue; // 주식 평가액
    private BigDecimal totalAsset; // 총 자산
    private Double totalProfitRate; // 총 수익률 (%)

    private List<PortfolioItemResponse> items; // 보유 종목 리스트

    @Getter
    @Builder
    public static class PortfolioItemResponse {
        private String stockCode;
        private String stockName;
        private Long quantity;
        private BigDecimal averagePrice; // 평균 단가
        private BigDecimal currentPrice; // 현재가
        private BigDecimal totalValue; // 평가액
        private Double profitRate; // 수익률 (%)

        public static PortfolioItemResponse of(Portfolio portfolio, BigDecimal currentPrice) {
            return PortfolioItemResponse.builder()
                    .stockCode(portfolio.getStock().getStockCode())
                    .stockName(portfolio.getStock().getStockName())
                    .quantity(portfolio.getQuantity())
                    .averagePrice(portfolio.getAvgPurchasePrice())
                    .currentPrice(currentPrice)
                    .totalValue(portfolio.calculateCurrentValue(currentPrice))
                    .profitRate(portfolio.calculateProfitRate(currentPrice))
                    .build();
        }
    }
}
