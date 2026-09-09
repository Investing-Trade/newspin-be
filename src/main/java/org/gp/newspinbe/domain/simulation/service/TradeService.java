package org.gp.newspinbe.domain.simulation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.gp.newspinbe.domain.simulation.domain.Portfolio;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.domain.Trade;
import org.gp.newspinbe.domain.simulation.domain.TradeType;
import org.gp.newspinbe.domain.simulation.dto.request.TradeRequest;
import org.gp.newspinbe.domain.simulation.dto.response.TradeResponse;
import org.gp.newspinbe.domain.simulation.repository.PortfolioRepository;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.simulation.repository.TradeRepository;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.domain.StockPrice;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TradeService {

    private final SimulationSessionRepository sessionRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;

    @Transactional
    public TradeResponse executeTrade(Long sessionId, Long userId, TradeRequest request) {
        log.info("거래 요청 - session: {}, stock: {}, type: {}", sessionId, request.getStockCode(),
                request.getTradeType());

        // 같은 세션의 동시 거래를 직렬화 (R-4: 잔고 lost update 방지)
        SimulationSession session = getSessionForUpdate(sessionId, userId);

        Stock stock = stockRepository.findByStockCode(request.getStockCode())
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));

        BigDecimal currentPrice = getCurrentPrice(stock, session.getCurrentSimulationDate());
        // 클라이언트가 보낸 가격은 신뢰하지 않는다. 서버 시세와 허용 오차 안에 있는지만 검증하고,
        // 체결은 항상 서버 시세로 한다 (C-3).
        validatePrice(request.getPrice(), currentPrice);
        Trade trade;
        if (request.getTradeType() == TradeType.BUY) {
            trade = executeBuy(session, stock, request.getQuantity(), currentPrice);
        } else {
            trade = executeSell(session, stock, request.getQuantity(), currentPrice);
        }

        return TradeResponse.from(trade, session.getCurrentCapital());
    }

    private Trade executeSell(SimulationSession session, Stock stock, Long quantity, BigDecimal price) {
        Portfolio portfolio = portfolioRepository.findBySessionAndStock(session, stock)
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_STOCK_QUANTITY));

        if (portfolio.getQuantity() < quantity) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK_QUANTITY);
        }

        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

        // 세션 잔고 증가
        session.increaseCapital(totalAmount);

        // 포트폴리오 업데이트
        portfolio.removeStock(quantity);
        if (portfolio.getQuantity() == 0) {
            portfolioRepository.delete(portfolio); // 전량 매도 시 포트폴리오 삭제
        } else {
            portfolioRepository.save(portfolio);
        }

        // 거래 기록 생성
        Trade trade = Trade.createTrade(
                session,
                stock,
                TradeType.SELL,
                quantity,
                price,
                session.getCurrentSimulationDate());
        return tradeRepository.save(trade);
    }

    private Trade executeBuy(SimulationSession session, Stock stock, Long quantity, BigDecimal price) {
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

        if (session.getCurrentCapital().compareTo(totalAmount) < 0) {
            throw new CustomException(ErrorCode.INSUFFICIENT_CAPITAL);
        }

        // 세션 잔고 차감
        session.decreaseCapital(totalAmount);

        // 포트폴리오 업데이트
        Portfolio portfolio = portfolioRepository.findBySessionAndStock(session, stock)
                .orElse(Portfolio.createPortfolio(session, stock)); // 빈 포트폴리오 생성
        portfolio.addStock(quantity, price);
        portfolioRepository.save(portfolio);

        // 거래 기록 생성
        Trade trade = Trade.createTrade(
                session,
                stock,
                TradeType.BUY,
                quantity,
                price,
                session.getCurrentSimulationDate());
        return tradeRepository.save(trade);
    }

    // 거래 내역 조회
    public java.util.List<TradeResponse> getTradeHistory(Long sessionId, Long userId) {
        SimulationSession session = getSession(sessionId, userId);

        return tradeRepository.findBySessionOrderByCreatedAtAsc(session)
                .stream()
                .map(trade -> TradeResponse.from(trade, null)) // 조회 시 잔고는 null
                .collect(java.util.stream.Collectors.toList());
    }

    private SimulationSession getSession(Long sessionId, Long userId) {
        return validate(sessionRepository.findById(sessionId), userId);
    }

    private SimulationSession getSessionForUpdate(Long sessionId, Long userId) {
        return validate(sessionRepository.findByIdForUpdate(sessionId), userId);
    }

    private SimulationSession validate(java.util.Optional<SimulationSession> found, Long userId) {
        SimulationSession session = found
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        if (session.getStatus() != org.gp.newspinbe.domain.simulation.domain.SessionStatus.ACTIVE) {
            throw new CustomException(ErrorCode.INVALID_SESSION_STATUS);
        }

        return session;
    }

    // 현재가 조회 (시뮬레이션 날짜 기준 종가)
    private BigDecimal getCurrentPrice(Stock stock, LocalDate simulationDate) {
    return stockPriceRepository.findByStockAndPriceDate(stock, simulationDate)
            .or(() -> stockPriceRepository.findFirstByStockAndPriceDateBeforeOrderByPriceDateDesc(stock, simulationDate))
            .map(StockPrice::getClosePrice)
            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TRADE));
    }

    /** 클라이언트가 화면에서 본 가격과 서버 시세의 허용 오차 (1%). */
    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.01");

    /**
     * 클라이언트 표시가와 서버 시세가 허용 오차 안인지 검증한다.
     * 정상 흐름에서 둘은 같은 일봉 종가라 거의 일치한다. 크게 어긋나면(스테일 데이터·조작)
     * 사용자가 의도한 가격이 아니므로 거절한다. 체결가는 이 값과 무관하게 서버 시세를 쓴다.
     */
    private void validatePrice(BigDecimal clientPrice, BigDecimal serverPrice) {
        if (clientPrice == null || serverPrice.signum() <= 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE);
        }
        BigDecimal diffRatio = clientPrice.subtract(serverPrice).abs()
                .divide(serverPrice, 6, RoundingMode.HALF_UP);
        if (diffRatio.compareTo(PRICE_TOLERANCE) > 0) {
            log.warn("가격 불일치 - client: {}, server: {}, diff: {}%", clientPrice, serverPrice,
                    diffRatio.movePointRight(2));
            throw new CustomException(ErrorCode.PRICE_MISMATCH);
        }
    }
}
