package org.gp.newspinbe.domain.simulation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.gp.newspinbe.domain.simulation.domain.Portfolio;
import org.gp.newspinbe.domain.simulation.domain.SessionStatus;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.dto.response.PortfolioOverviewResponse;
import org.gp.newspinbe.domain.simulation.repository.PortfolioRepository;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.stock.application.StockPriceResolver;
import org.gp.newspinbe.domain.stock.domain.Stock;
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
public class PortfolioService {

    private final SimulationSessionRepository sessionRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockPriceResolver stockPriceResolver;

    public PortfolioOverviewResponse getPortfolioOverview(Long sessionId, Long userId) {
        SimulationSession session = getSession(sessionId, userId);

        // 보유 종목 리스트 조회
        List<Portfolio> portfolios = portfolioRepository.findBySessionWithStock(session);

        List<PortfolioOverviewResponse.PortfolioItemResponse> items = new ArrayList<>();
        BigDecimal totalStockValue = BigDecimal.ZERO;

        LocalDate currentDate = session.getCurrentSimulationDate();

        // 각 종목별 현재가 조회 및 계산
        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity() <= 0)
                continue; // 수량 0인 것은 제외

            BigDecimal currentPrice = getCurrentPrice(portfolio.getStock(), currentDate);

            PortfolioOverviewResponse.PortfolioItemResponse item = PortfolioOverviewResponse.PortfolioItemResponse
                    .of(portfolio, currentPrice);

            items.add(item);

            // 총 평가액 누적
            totalStockValue = totalStockValue.add(item.getTotalValue());
        }

        // 총 자산 및 수익률 계산
        BigDecimal currentCapital = session.getCurrentCapital();
        BigDecimal totalAsset = currentCapital.add(totalStockValue);
        BigDecimal initialCapital = session.getInitialCapital();

        Double totalProfitRate = 0.0;
        if (initialCapital.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitRate = totalAsset.subtract(initialCapital)
                    .divide(initialCapital, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        return PortfolioOverviewResponse.builder()
                .currentCapital(currentCapital)
                .totalStockValue(totalStockValue)
                .totalAsset(totalAsset)
                .totalProfitRate(totalProfitRate)
                .items(items)
                .build();
    }

    private SimulationSession getSession(Long sessionId, Long userId) {
        SimulationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }
        return session;
    }

    private BigDecimal getCurrentPrice(Stock stock, LocalDate date) {
        return stockPriceResolver.closeForValuation(stock, date);
    }
}
