package org.gp.newspinbe.domain.simulation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.gp.newspinbe.domain.event.dto.response.EventResponse;
import org.gp.newspinbe.domain.event.service.EventService;
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
    private final EventService eventService;

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

        // 3. 뉴스 및 이벤트 조회
        List<NewsResponse> todayNews = getNewsWithEvents(currentDate);

        return DayResponse.from(session, todayHistory, yesterdayHistory, todayNews);
    }

    @Transactional
    public DayResponse proceedToNextDay(Long sessionId, Long userId) {
        SimulationSession session = getSession(sessionId, userId);

        // 0. 진행 전(어제) 자산 데이터 확보
        AssetHistory yesterdayHistory = assetHistoryRepository
                .findFirstBySessionAndRecordDateLessThanOrderByRecordDateDesc(session,
                        session.getCurrentSimulationDate().plusDays(1))
                .orElse(null);

        // 1. 날짜 이동
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

        // 4. 뉴스 및 이벤트 조회
        List<NewsResponse> todayNews = getNewsWithEvents(nextDate);

        return DayResponse.from(session, todayHistory, yesterdayHistory, todayNews);
    }

    // 자산 평가 및 히스토리 기록 (DB 저장)
    private AssetHistory calculateAndRecordAsset(SimulationSession session, LocalDate date) {
        BigDecimal totalStockValue = calculateTotalStockValue(session, date);

        // AssetHistory 생성 및 저장
        AssetHistory history = AssetHistory.createHistory(
                session,
                date,
                session.getCurrentCapital(),
                totalStockValue);
        return assetHistoryRepository.save(history);
    }

    // 총 주식 평가액 계산 (공통)
    private BigDecimal calculateTotalStockValue(SimulationSession session, LocalDate date) {
        List<Portfolio> portfolios = portfolioRepository.findBySessionWithStock(session);
        BigDecimal totalStockValue = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            Stock stock = portfolio.getStock();
            // 해당 날짜의 종가 조회 (데이터 없으면 0원 처리 or 에러)
            BigDecimal closePrice = stockPriceRepository.findByStockAndPriceDate(stock, date)
                    .map(StockPrice::getClosePrice)
                    .orElse(BigDecimal.ZERO); // 데이터 없으면 0으로 처리 (유연하게)

            totalStockValue = totalStockValue.add(
                    closePrice.multiply(BigDecimal.valueOf(portfolio.getQuantity())));
        }
        return totalStockValue;
    }

    // 뉴스 및 이벤트 조회 (공통)
    private List<NewsResponse> getNewsWithEvents(LocalDate date) {
        List<NewsResponse> newsList = new java.util.ArrayList<>(
                newsRepository.findByArticleDate(date).stream()
                        .map(NewsResponse::from)
                        .collect(Collectors.toList()));

        eventService.findEventByDate(date).ifPresent(marketEvent -> {
            NewsResponse eventNews = NewsResponse.builder()
                    .newsId(null)
                    .title("[속보] " + marketEvent.getEventName())
                    .content(marketEvent.getDescription())
                    .articleDate(marketEvent.getEventDate())
                    .source("MARKET_EVENT")
                    .isEvent(true)
                    .build();
            newsList.add(0, eventNews);
        });

        return newsList;
    }

    private SimulationSession getSession(Long sessionId, Long userId) {
        SimulationSession session = sessionRepository.findById(sessionId)
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
