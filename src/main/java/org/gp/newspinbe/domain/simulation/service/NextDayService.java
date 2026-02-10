package org.gp.newspinbe.domain.simulation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.dto.response.NewsResponse;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.simulation.domain.AssetHistory;
import org.gp.newspinbe.domain.simulation.domain.Portfolio;
import org.gp.newspinbe.domain.simulation.domain.SessionStatus;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.dto.response.DayResponse;
import org.gp.newspinbe.domain.simulation.repository.AssetHistoryRepository;
import org.gp.newspinbe.domain.simulation.repository.PortfolioRepository;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.domain.StockPrice;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
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
public class NextDayService {

    private final SimulationSessionRepository sessionRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockPriceRepository stockPriceRepository;
    private final NewsArticleRepository newsRepository;

    @Transactional
    public DayResponse proceedToNextDay(Long sessionId, Long userId) {
        SimulationSession session = getSession(sessionId, userId);

        // 0. 전일 자산 데이터 조회 (날짜 업데이트 전)
        AssetHistory yesterdayHistory = assetHistoryRepository.findFirstBySessionOrderByRecordDateDesc(session)
                .orElse(null);

        // 1. 날짜 이동 (하루씩 이동, 주말 로직은 데이터에 의존하거나 별도 유틸로 처리)
        LocalDate nextDate = session.getCurrentSimulationDate().plusDays(1);

        // 주말 체크 (토, 일이면 월요일로 이동)
        while (isWeekend(nextDate)) {
            nextDate = nextDate.plusDays(1);
        }

        session.updateCurrentSimulationDate(nextDate);

        // 2. 종료 조건 확인
        if (nextDate.isAfter(session.getEndDate())) {
            session.complete();
            log.info("세션 종료됨 - sessionId: {}", sessionId);
        }

        // 3. 자산 평가 및 기록
        AssetHistory todayHistory = calculateAndRecordAsset(session, nextDate);

        // 4. 오늘의 뉴스 조회
        List<NewsResponse> todayNews = getTodayNews(nextDate);

        return DayResponse.from(session, todayHistory, yesterdayHistory, todayNews);
    }

    // 자산 평가 및 히스토리 기록
    private AssetHistory calculateAndRecordAsset(SimulationSession session, LocalDate date) {
        // 보유 포트폴리오 조회 (Stock Fetch Join)
        List<Portfolio> portfolios = portfolioRepository.findBySessionWithStock(session);

        // 총 주식 평가금액 계산
        BigDecimal totalStockValue = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            Stock stock = portfolio.getStock();
            // 해당 날짜의 종가 조회
            BigDecimal closePrice = getClosePrice(stock, date);

            // 평가액 누적
            totalStockValue = totalStockValue.add(
                    closePrice.multiply(BigDecimal.valueOf(portfolio.getQuantity())));
        }

        // AssetHistory 생성 및 저장 (현금 + 주식평가액)
        AssetHistory history = AssetHistory.createHistory(
                session,
                date,
                session.getCurrentCapital(),
                totalStockValue);
        return assetHistoryRepository.save(history);
    }

    // 해당 날짜의 종가 조회 (데이터가 없으면 전일 종가 혹은 에러 처리)
    private BigDecimal getClosePrice(Stock stock, LocalDate date) {
        return stockPriceRepository.findByStockAndPriceDate(stock, date)
                .map(StockPrice::getClosePrice)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_DATE_RANGE)); // 데이터 없음
    }

    // 오늘의 뉴스 조회
    private List<NewsResponse> getTodayNews(LocalDate date) {
        return newsRepository.findByArticleDate(date).stream()
                .map(NewsResponse::from)
                .collect(Collectors.toList());
    }

    private SimulationSession getSession(Long sessionId, Long userId) {
        SimulationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new CustomException(ErrorCode.INVALID_SESSION_STATUS);
        }
        return session;
    }

    private boolean isWeekend(LocalDate date) {
        java.time.DayOfWeek day = date.getDayOfWeek();
        return day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY;
    }
}
