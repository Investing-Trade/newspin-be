package org.gp.newspinbe.domain.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.gp.newspinbe.domain.stock.application.StockService;
import org.gp.newspinbe.domain.stock.dto.response.StockPriceHistoryResponse;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 착수 시점 동작 고정 (C-5 / S-1). 현재 {@code /stocks/{code}/price-range} 는
 * 기준일 이후 영업일 시세를 그대로 반환한다 = 미래 정보 유출(lookahead).
 *
 * <p>스테이지 1에서 "기준일 이후 데이터는 반환하지 않는다"로 고칠 때 이 테스트의
 * 기대값을 뒤집는다. 지금은 "결함이 존재함"을 못박아 회귀를 막는 용도.
 */
@IntegrationTest
class StockServiceLookaheadCharacterizationTest {

    @Autowired StockService stockService;

    private static final String SAMSUNG = "005930";
    private static final LocalDate TARGET = LocalDate.parse("2020-02-03");

    @Test
    void 기준일_이후_시세가_응답에_포함된다_현재_동작() {
        StockPriceHistoryResponse response = stockService.getStockPriceHistoryAroundDate(SAMSUNG, TARGET);

        boolean hasFuture = response.getPrices().stream()
                .anyMatch(p -> p.getDate().isAfter(TARGET));

        assertThat(hasFuture)
                .as("현재 구현은 기준일 이후 5영업일 시세를 노출한다 (스테이지 1에서 차단 예정)")
                .isTrue();
    }
}
