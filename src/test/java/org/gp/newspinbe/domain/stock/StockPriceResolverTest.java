package org.gp.newspinbe.domain.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.gp.newspinbe.domain.stock.application.StockPriceResolver;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** R-5. 시세 결측 처리를 한 곳에서 일관되게. */
@IntegrationTest
class StockPriceResolverTest {

    @Autowired StockPriceResolver resolver;
    @Autowired StockRepository stockRepository;

    private Stock samsung() {
        return stockRepository.findByStockCode("005930").orElseThrow();
    }

    @Test
    void 거래일이면_그날_종가() {
        assertThat(resolver.resolveCloseAsOf(samsung(), LocalDate.parse("2020-02-03"))).isPresent();
    }

    @Test
    void 주말이면_직전_영업일_종가로_폴백() {
        // 2020-02-08 은 토요일 → 2020-02-07(금) 종가
        assertThat(resolver.resolveCloseAsOf(samsung(), LocalDate.parse("2020-02-08"))).isPresent();
    }

    @Test
    void 데이터_이전_시점이면_empty() {
        assertThat(resolver.resolveCloseAsOf(samsung(), LocalDate.parse("2019-01-01"))).isEmpty();
        assertThat(resolver.closeForValuation(samsung(), LocalDate.parse("2019-01-01")))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }
}
