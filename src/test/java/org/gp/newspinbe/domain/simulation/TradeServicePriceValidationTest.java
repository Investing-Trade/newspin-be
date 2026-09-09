package org.gp.newspinbe.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.gp.newspinbe.support.IntegrationTest;

/**
 * C-3. 클라이언트가 보낸 가격을 무조건 신뢰하던 것을 → 서버 시세와 허용 오차(1%) 검증으로 재설계.
 * 체결가는 항상 서버 시세.
 */
@IntegrationTest
class TradeServicePriceValidationTest {

    @Autowired TradeService tradeService;
    @Autowired UserRepository userRepository;
    @Autowired SimulationSessionRepository sessionRepository;
    @Autowired StockRepository stockRepository;
    @Autowired StockPriceRepository stockPriceRepository;

    private static final String STOCK = "005930";
    private static final LocalDate AS_OF = LocalDate.parse("2020-02-03");

    private Long userId;
    private Long sessionId;
    private BigDecimal serverPrice;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.create("trade-price-" + System.nanoTime() + "@t.com", "x"));
        userId = user.getUserId();
        SimulationSession session = SimulationSession.createSession(
                user, new BigDecimal("100000000"), AS_OF, LocalDate.parse("2020-03-31"));
        sessionId = sessionRepository.save(session).getSessionId();

        Stock stock = stockRepository.findByStockCode(STOCK).orElseThrow();
        serverPrice = stockPriceRepository.findByStockAndPriceDate(stock, AS_OF).orElseThrow().getClosePrice();
    }

    @Test
    void 서버_시세와_같은_가격이면_서버_시세로_체결된다() {
        TradeResponse res = tradeService.executeTrade(sessionId, userId,
                new TradeRequest(STOCK, TradeType.BUY, 1L, serverPrice));

        assertThat(res.getPrice()).isEqualByComparingTo(serverPrice);
    }

    @Test
    void 허용_오차_안이면_통과하고_체결가는_서버_시세다() {
        BigDecimal slightlyOff = serverPrice.multiply(new BigDecimal("1.005")); // +0.5%
        TradeResponse res = tradeService.executeTrade(sessionId, userId,
                new TradeRequest(STOCK, TradeType.BUY, 1L, slightlyOff));

        assertThat(res.getPrice()).isEqualByComparingTo(serverPrice);
    }

    @Test
    void 오차를_벗어난_가격은_거절된다() {
        BigDecimal wayOff = serverPrice.multiply(new BigDecimal("1.10")); // +10%
        assertThatThrownBy(() -> tradeService.executeTrade(sessionId, userId,
                new TradeRequest(STOCK, TradeType.BUY, 1L, wayOff)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRICE_MISMATCH);
    }
}
