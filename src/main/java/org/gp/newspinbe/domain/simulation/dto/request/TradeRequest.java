package org.gp.newspinbe.domain.simulation.dto.request;

import java.math.BigDecimal;

import org.gp.newspinbe.domain.simulation.domain.TradeType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TradeRequest {

    @NotNull(message = "종목 코드는 필수입니다")
    private String stockCode;

    @NotNull(message = "거래 유형은 필수입니다 (BUY/SELL)")
    private TradeType tradeType;

    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1주 이상이어야 합니다")
    private Long quantity;

    @NotNull(message = "가격은 필수입니다")
    @Min(value = 1, message = "가격은 1원 이상이어야 합니다")
    private BigDecimal price;
}
