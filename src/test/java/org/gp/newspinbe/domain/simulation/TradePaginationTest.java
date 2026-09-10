package org.gp.newspinbe.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.domain.TradeType;
import org.gp.newspinbe.domain.simulation.dto.request.TradeRequest;
import org.gp.newspinbe.domain.simulation.dto.response.TradeResponse;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.simulation.service.TradeService;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.databind.ObjectMapper;

/** I-10. 거래 내역이 많아도 요청한 페이지만 조회한다. */
@IntegrationTest
class TradePaginationTest {

    @Autowired TradeService tradeService;
    @Autowired UserRepository userRepository;
    @Autowired SimulationSessionRepository sessionRepository;
    @Autowired StockRepository stockRepository;
    @Autowired StockPriceRepository stockPriceRepository;

    private static final LocalDate AS_OF = LocalDate.parse("2020-02-03");

    @Test
    void 거래_내역은_페이지_단위로_조회된다() {
        User user = userRepository.save(User.create("page-" + System.nanoTime() + "@t.com", "x"));
        SimulationSession session = sessionRepository.save(SimulationSession.createSession(
                user, new BigDecimal("50000000"), AS_OF, LocalDate.parse("2020-03-31")));
        Stock stock = stockRepository.findByStockCode("005930").orElseThrow();
        BigDecimal price = stockPriceRepository.findByStockAndPriceDate(stock, AS_OF).orElseThrow().getClosePrice();
        ObjectMapper om = new ObjectMapper();
        TradeRequest req = om.convertValue(
                Map.of("stockCode", "005930", "tradeType", TradeType.BUY, "quantity", 1, "price", price),
                TradeRequest.class);
        for (int i = 0; i < 25; i++) {
            tradeService.executeTrade(session.getSessionId(), user.getUserId(), req);
        }

        Page<TradeResponse> first = tradeService.getTradeHistory(session.getSessionId(), user.getUserId(),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<TradeResponse> second = tradeService.getTradeHistory(session.getSessionId(), user.getUserId(),
                PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(first.getTotalElements()).isEqualTo(25);
        assertThat(first.getContent()).hasSize(20);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).hasSize(5);
        assertThat(second.hasNext()).isFalse();
    }
}
