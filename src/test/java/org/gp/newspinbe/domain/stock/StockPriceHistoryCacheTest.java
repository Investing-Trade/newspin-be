package org.gp.newspinbe.domain.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.gp.newspinbe.domain.stock.application.StockService;
import org.gp.newspinbe.global.config.CacheConfig;
import org.gp.newspinbe.support.IntegrationTest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import jakarta.persistence.EntityManagerFactory;

/** I-3. 과거 확정 시세라 두 번째 조회는 캐시에서 나온다 (테스트 프로필은 인메모리 캐시). */
@IntegrationTest
class StockPriceHistoryCacheTest {

    @Autowired StockService stockService;
    @Autowired CacheManager cacheManager;
    @Autowired EntityManagerFactory emf;

    @Test
    void 두번째_조회는_DB를_치지_않는다() {
        cacheManager.getCache(CacheConfig.PRICE_HISTORY).clear();
        LocalDate asOf = LocalDate.parse("2020-02-10");

        stockService.getStockPriceHistoryUpTo("005930", asOf); // miss

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        stockService.getStockPriceHistoryUpTo("005930", asOf); // hit

        assertThat(stats.getPrepareStatementCount()).as("캐시 히트 — 쿼리 0").isZero();
        assertThat(cacheManager.getCache(CacheConfig.PRICE_HISTORY).get("005930:" + asOf)).isNotNull();
    }
}
