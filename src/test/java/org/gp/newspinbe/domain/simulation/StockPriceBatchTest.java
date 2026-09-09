package org.gp.newspinbe.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.domain.TradeType;
import org.gp.newspinbe.domain.simulation.dto.request.TradeRequest;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.simulation.service.PortfolioService;
import org.gp.newspinbe.domain.simulation.service.TradeService;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManagerFactory;

/** I-2. 보유 종목이 늘어도 포트폴리오 조회 쿼리 수가 일정한지. */
@IntegrationTest
class StockPriceBatchTest {

    @Autowired TradeService tradeService;
    @Autowired PortfolioService portfolioService;
    @Autowired UserRepository userRepository;
    @Autowired SimulationSessionRepository sessionRepository;
    @Autowired StockRepository stockRepository;
    @Autowired StockPriceRepository stockPriceRepository;
    @Autowired EntityManagerFactory emf;

    private static final LocalDate AS_OF = LocalDate.parse("2020-02-03");
    private static final List<String> CODES =
            List.of("005930", "000100", "003490", "079160", "139480", "035420");

    @Test
    void 보유_6종목_포트폴리오_조회는_소수_쿼리로() {
        User user = userRepository.save(User.create("batch-" + System.nanoTime() + "@t.com", "x"));
        SimulationSession session = sessionRepository.save(SimulationSession.createSession(
                user, new BigDecimal("100000000"), AS_OF, LocalDate.parse("2020-03-31")));
        ObjectMapper om = new ObjectMapper();

        for (String code : CODES) {
            Stock stock = stockRepository.findByStockCode(code).orElseThrow();
            BigDecimal price = stockPriceRepository.findByStockAndPriceDate(stock, AS_OF).orElseThrow().getClosePrice();
            TradeRequest req = om.convertValue(
                    Map.of("stockCode", code, "tradeType", TradeType.BUY, "quantity", 1, "price", price),
                    TradeRequest.class);
            tradeService.executeTrade(session.getSessionId(), user.getUserId(), req);
        }

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        var overview = portfolioService.getPortfolioOverview(session.getSessionId(), user.getUserId());

        assertThat(overview.getItems()).hasSize(6);
        assertThat(stats.getPrepareStatementCount())
                .as("6종목이어도 쿼리는 상수 (세션 + 포트폴리오 + 시세배치)")
                .isLessThanOrEqualTo(5);
    }
}
