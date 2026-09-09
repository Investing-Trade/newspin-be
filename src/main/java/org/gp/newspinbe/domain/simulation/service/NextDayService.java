package org.gp.newspinbe.domain.simulation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.gp.newspinbe.domain.news.dto.response.NewsResponse;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.simulation.domain.AssetHistory;
import org.gp.newspinbe.domain.simulation.domain.Portfolio;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.dto.response.DayResponse;
import org.gp.newspinbe.domain.simulation.repository.AssetHistoryRepository;
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
public class NextDayService {

    private final SimulationSessionRepository sessionRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockPriceResolver stockPriceResolver;
    private final NewsArticleRepository newsRepository;

    // 현재 날짜의 데이터 조회 (저장 없음)
    public DayResponse getCurrentDayData(Long sessionId, Long userId) {
        SimulationSession session = getSession(sessionId, userId);
        LocalDate currentDate = session.getCurrentSimulationDate();

        // 1. 자산 데이터 조회 (없으면 실시간 계산)
        AssetHistory todayHistory = assetHistoryRepository.findBySessionAndRecordDate(session, currentDate)
                .orElseGet(() -> {
                    BigDecimal totalStockValue = calculateTotalStockValue(session, currentDate);
                    // 저장되지 않은 임시 객체 생성
                    return AssetHistory.createHistory(
                            session,
                            currentDate,
                            session.getCurrentCapital(),
                            totalStockValue);
                });

        // 2. 비교 대상(어제) 자산 데이터 조회
        AssetHistory yesterdayHistory = assetHistoryRepository
                .findFirstBySessionAndRecordDateLessThanOrderByRecordDateDesc(session, currentDate)
                .orElse(null);

        // 3. 뉴스 조회 (이벤트 뉴스도 자동 포함)
        List<NewsResponse> todayNews = getTodayNews(currentDate);

        return DayResponse.from(session, todayHistory, yesterdayHistory, todayNews);
    }

    @Transactional
    public DayResponse proceedToNextDay(Long sessionId, Long userId) {
        // 같은 세션의 동시 진행을 직렬화 (R-3: AssetHistory 유니크 위반 방지)
        SimulationSession session = getSessionForUpdate(sessionId, userId);

        // 0. 진행 전(어제) 자산 데이터 확보
        AssetHistory yesterdayHistory = assetHistoryRepository
                .findFirstBySessionAndRecordDateLessThanOrderByRecordDateDesc(session,
                        session.getCurrentSimulationDate().plusDays(1))
                .orElse(null);

        // 1. 날짜 이동 (주말 건너뛰기)
        LocalDate nextDate = session.getCurrentSimulationDate().plusDays(1);
        while (isWeekend(nextDate)) {
            nextDate = nextDate.plusDays(1);
        }
        session.updateCurrentSimulationDate(nextDate);

        // 2. 종료 조건 확인
        if (nextDate.isAfter(session.getEndDate())) {
            session.complete();
            log.info("세션 종료됨 - sessionId: {}", sessionId);
        }

        // 3. 자산 평가 및 기록 (DB 저장)
        AssetHistory todayHistory = calculateAndRecordAsset(session, nextDate);

        // 4. 뉴스 조회 (이벤트 뉴스도 자동 포함)
        List<NewsResponse> todayNews = getTodayNews(nextDate);

        return DayResponse.from(session, todayHistory, yesterdayHistory, todayNews);
    }

    // 자산 평가 및 히스토리 기록 (DB 저장)
    private AssetHistory calculateAndRecordAsset(SimulationSession session, LocalDate date) {
        BigDecimal totalStockValue = calculateTotalStockValue(session, date);

        AssetHistory history = AssetHistory.createHistory(
                session,
                date,
                session.getCurrentCapital(),
                totalStockValue);
        return assetHistoryRepository.save(history);
    }

    // 총 주식 평가액 계산
    private BigDecimal calculateTotalStockValue(SimulationSession session, LocalDate date) {
        List<Portfolio> portfolios = portfolioRepository.findBySessionWithStock(session);
        BigDecimal totalStockValue = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            Stock stock = portfolio.getStock();
            BigDecimal closePrice = stockPriceResolver.closeForValuation(stock, date);

            totalStockValue = totalStockValue.add(
                    closePrice.multiply(BigDecimal.valueOf(portfolio.getQuantity())));
        }
        return totalStockValue;
    }

    // 오늘의 뉴스 조회 (이벤트 뉴스도 NewsArticle이므로 자동 포함)
    private List<NewsResponse> getTodayNews(LocalDate date) {
        return newsRepository.findByArticleDate(date).stream()
                .map(NewsResponse::from)
                .collect(Collectors.toList());
    }

    private SimulationSession getSession(Long sessionId, Long userId) {
        return validateOwner(sessionRepository.findById(sessionId), userId);
    }

    private SimulationSession getSessionForUpdate(Long sessionId, Long userId) {
        return validateOwner(sessionRepository.findByIdForUpdate(sessionId), userId);
    }

    private SimulationSession validateOwner(java.util.Optional<SimulationSession> found, Long userId) {
        SimulationSession session = found
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }
        return session;
    }

    private boolean isWeekend(LocalDate date) {
        java.time.DayOfWeek day = date.getDayOfWeek();
        return day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY;
    }
}
