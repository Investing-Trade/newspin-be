package org.gp.newspinbe.domain.stock.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockPriceHistoryItem {
    private final LocalDate date;
    private final BigDecimal openPrice;
    private final BigDecimal closePrice;
    private final BigDecimal highPrice;
    private final BigDecimal lowPrice;
    private final Long volume;
    private final Double dailyChangeRate; // 전일 대비 변동률 (%)
    private final Double baseChangeRate;  // 기준일 대비 변동률 (%)
    private final Boolean isEventDate;    // 기준일 여부
}
