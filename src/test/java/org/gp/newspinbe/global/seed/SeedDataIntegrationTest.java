package org.gp.newspinbe.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.gp.newspinbe.domain.event.repository.EventStockImpactRepository;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 시드 적재 결과 고정. 컨텍스트 로드 = Flyway baseline 이 엔티티와 일치함을 의미.
 */
@IntegrationTest
class SeedDataIntegrationTest {

    @Autowired StockRepository stockRepository;
    @Autowired NewsArticleRepository newsArticleRepository;
    @Autowired StockPriceRepository stockPriceRepository;
    @Autowired EventStockImpactRepository eventStockImpactRepository;

    @Test
    void 시드가_적재된다() {
        assertThat(stockRepository.count()).isEqualTo(20);            // 원본 21 - 상장 전 LG CNS
        assertThat(newsArticleRepository.count()).isEqualTo(2528);    // 2544 - 064400 참조 16
        assertThat(stockPriceRepository.count()).isEqualTo(1240);     // 20종목 x 62거래일
        assertThat(eventStockImpactRepository.count()).isEqualTo(107);
        assertThat(newsArticleRepository.findByArticleDateBetweenAndEventTypeIsNotNull(
                java.time.LocalDate.parse("2020-01-01"), java.time.LocalDate.parse("2020-03-31")))
                .hasSize(10);
    }
}
