package org.gp.newspinbe.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.domain.TradeType;
import org.gp.newspinbe.domain.simulation.dto.request.TradeRequest;
import org.gp.newspinbe.domain.simulation.repository.AssetHistoryRepository;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.simulation.repository.TradeRepository;
import org.gp.newspinbe.domain.simulation.service.NextDayService;
import org.gp.newspinbe.domain.simulation.service.TradeService;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

/** R-3 / R-4. 같은 세션에 대한 동시 쓰기가 비관적 락으로 직렬화되는지. */
@IntegrationTest
class SessionConcurrencyTest {

    @Autowired TradeService tradeService;
    @Autowired NextDayService nextDayService;
    @Autowired UserRepository userRepository;
    @Autowired SimulationSessionRepository sessionRepository;
    @Autowired TradeRepository tradeRepository;
    @Autowired AssetHistoryRepository assetHistoryRepository;
    @Autowired StockRepository stockRepository;
    @Autowired StockPriceRepository stockPriceRepository;

    private static final String STOCK = "005930";
    private static final LocalDate START = LocalDate.parse("2020-02-03");

    @Test
    void 동시_매수시_잔고가_음수가_되지_않는다() throws Exception {
        User user = userRepository.save(User.create("concur-buy-" + System.nanoTime() + "@t.com", "x"));
        Stock stock = stockRepository.findByStockCode(STOCK).orElseThrow();
        BigDecimal price = stockPriceRepository.findByStockAndPriceDate(stock, START).orElseThrow().getClosePrice();

        // 정확히 3주치 + 여유 100원
        BigDecimal capital = price.multiply(BigDecimal.valueOf(3)).add(BigDecimal.valueOf(100));
        SimulationSession session = sessionRepository.save(
                SimulationSession.createSession(user, capital, START, LocalDate.parse("2020-03-31")));

        TradeRequest req = new ObjectMapper().convertValue(
                Map.of("stockCode", STOCK, "tradeType", TradeType.BUY, "quantity", 1, "price", price),
                TradeRequest.class);

        int threads = 10;
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        runConcurrently(threads, () -> {
            try {
                tradeService.executeTrade(session.getSessionId(), user.getUserId(), req);
                ok.incrementAndGet();
            } catch (RuntimeException e) {
                fail.incrementAndGet();
            }
            return null;
        });

        SimulationSession reloaded = sessionRepository.findById(session.getSessionId()).orElseThrow();
        assertThat(ok.get()).isEqualTo(3);
        assertThat(fail.get()).isEqualTo(threads - 3);
        assertThat(reloaded.getCurrentCapital()).isEqualByComparingTo(
                capital.subtract(price.multiply(BigDecimal.valueOf(3))));
        assertThat(reloaded.getCurrentCapital().signum()).isGreaterThanOrEqualTo(0);
        assertThat(tradeRepository.findBySessionOrderByCreatedAtAsc(reloaded)).hasSize(3);
    }

    @Test
    void 동시_다음날_진행시_유니크_위반없이_직렬화된다() throws Exception {
        User user = userRepository.save(User.create("concur-day-" + System.nanoTime() + "@t.com", "x"));
        SimulationSession session = sessionRepository.save(SimulationSession.createSession(
                user, BigDecimal.valueOf(10_000_000), START, LocalDate.parse("2020-03-31")));

        int threads = 5;
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        runConcurrently(threads, () -> {
            try {
                nextDayService.proceedToNextDay(session.getSessionId(), user.getUserId());
                ok.incrementAndGet();
            } catch (RuntimeException e) {
                conflict.incrementAndGet();
            }
            return null;
        });

        List<?> histories = assetHistoryRepository.findBySessionOrderByRecordDateAsc(
                sessionRepository.findById(session.getSessionId()).orElseThrow());
        // 직렬화되면 5회 모두 성공하고, 서로 다른 날짜의 히스토리 5건이 생긴다 (유니크 위반 500 없음)
        assertThat(ok.get()).isEqualTo(threads);
        assertThat(conflict.get()).isZero();
        assertThat(histories).hasSize(threads);
    }

    private void runConcurrently(int n, Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Void>> futures = pool.invokeAll(java.util.Collections.nCopies(n, task));
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
