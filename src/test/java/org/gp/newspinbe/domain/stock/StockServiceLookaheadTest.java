package org.gp.newspinbe.domain.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.gp.newspinbe.domain.stock.application.StockService;
import org.gp.newspinbe.domain.stock.dto.response.StockPriceHistoryResponse;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * C-5 / S-1 회귀 방지. {@code /stocks/price-range} 는 기준일 이후 시세를 반환하면 안 된다
 * (학습 시뮬레이션에서 판단 시점 이후의 정답을 미리 볼 수 없도록).
 */
@IntegrationTest
class StockServiceLookaheadTest {

    @Autowired StockService stockService;

    private static final String SAMSUNG = "005930";
    private static final LocalDate AS_OF = LocalDate.parse("2020-02-03");

    @Test
    void 기준일_이후_시세는_반환되지_않는다() {
        StockPriceHistoryResponse response = stockService.getStockPriceHistoryUpTo(SAMSUNG, AS_OF);

        assertThat(response.getPrices()).isNotEmpty();
        assertThat(response.getPrices())
                .as("모든 시세는 기준일 이하여야 한다")
                .allSatisfy(p -> assertThat(p.getDate()).isBeforeOrEqualTo(AS_OF));
        assertThat(response.getPrices().get(response.getPrices().size() - 1).getDate())
                .as("마지막 포인트는 기준일(거래일이면) 또는 그 직전 거래일")
                .isBeforeOrEqualTo(AS_OF);
    }

    @Test
    void 전체_종목_조회도_기준일_이후를_노출하지_않는다() {
        var responses = stockService.getAllStocksPriceHistoryUpTo(AS_OF);

        assertThat(responses).isNotEmpty();
        assertThat(responses).allSatisfy(r ->
                assertThat(r.getPrices()).allSatisfy(p ->
                        assertThat(p.getDate()).isBeforeOrEqualTo(AS_OF)));
    }
}
