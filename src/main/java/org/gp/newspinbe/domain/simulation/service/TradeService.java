package org.gp.newspinbe.domain.simulation.service;

import java.math.BigDecimal;
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

        SimulationSession session = getSession(sessionId, userId);

        Stock stock = stockRepository.findByStockCode(request.getStockCode())
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));

        BigDecimal currentPrice = getCurrentPrice(stock, session.getCurrentSimulationDate());
        validatePrice(request.getPrice(), currentPrice);

        Trade trade;
        if (request.getTradeType() == TradeType.BUY) {
            trade = executeBuy(session, stock, request.getQuantity(), currentPrice);
        } else {
            trade = executeSell(session, stock, request.getQuantity(), currentPrice);
        }

        return TradeResponse.from(trade, session.getCurrentCapital());
    }

    // executeSell 내부 수정
    private Trade executeSell(SimulationSession session, Stock stock, Long quantity, BigDecimal price) {
        Portfolio portfolio = portfolioRepository.findBySessionAndStock(session, stock)
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_STOCK_QUANTITY));

        // 보유 수량 확인
        if (portfolio.getQuantity() < quantity) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK_QUANTITY);
        }

        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

        // 1. 세션 잔고 증가
        session.increaseCapital(totalAmount);

        // 2. 포트폴리오 업데이트 (감소)
        portfolio.removeStock(quantity);
        if (portfolio.getQuantity() == 0) {
            portfolioRepository.delete(portfolio); // 전량 매도 시 포트폴리오 삭제
        } else {
            portfolioRepository.save(portfolio); // 수량 변경 저장
        }

        // 3. 거래 기록 생성
        Trade trade = Trade.createTrade(
                session,
                stock,
                TradeType.SELL,
                quantity,
                price,
                session.getCurrentSimulationDate());
        return tradeRepository.save(trade);
    }

    // executeBuy 에서 createPortfolio 호출 수정
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

    /**
     * 거래 내역 조회
     */
    public java.util.List<TradeResponse> getTradeHistory(Long sessionId, Long userId) {
        SimulationSession session = getSession(sessionId, userId);

        return tradeRepository.findBySessionOrderByCreatedAtAsc(session)
                .stream()
                .map(trade -> TradeResponse.from(trade, null)) // 조회 시 잔고는 null
                .collect(java.util.stream.Collectors.toList());
    }

    private SimulationSession getSession(Long sessionId, Long userId) {
        SimulationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        if (session.getStatus() != org.gp.newspinbe.domain.simulation.domain.SessionStatus.ACTIVE) {
            throw new CustomException(ErrorCode.INVALID_SESSION_STATUS);
        }

        return session;
    }

    private BigDecimal getCurrentPrice(Stock stock, LocalDate simulationDate) {
        return stockPriceRepository.findByStockAndPriceDate(stock, simulationDate)
                .map(StockPrice::getClosePrice)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TRADE));
    }

    private void validatePrice(BigDecimal requestPrice, BigDecimal currentPrice) {
        if (requestPrice.compareTo(currentPrice) != 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE);
        }
    }
}
