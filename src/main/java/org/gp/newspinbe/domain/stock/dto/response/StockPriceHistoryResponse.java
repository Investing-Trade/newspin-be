package org.gp.newspinbe.domain.stock.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockPriceHistoryResponse implements java.io.Serializable {
    private final Long stockId;
    private final String stockCode;
    private final String stockName;
    private final String sector;
    private final List<StockPriceHistoryItem> prices;
}
