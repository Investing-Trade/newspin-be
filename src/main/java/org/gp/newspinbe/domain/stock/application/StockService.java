package org.gp.newspinbe.domain.stock.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.domain.StockPrice;
import org.gp.newspinbe.domain.stock.dto.response.StockPriceHistoryItem;
import org.gp.newspinbe.domain.stock.dto.response.StockPriceHistoryResponse;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.gp.newspinbe.global.config.CacheConfig;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;

    /**
     * 기준일({@code asOfDate}) 시점의 주가 히스토리. 기준일 <b>이후</b> 시세는 반환하지 않는다 (C-5, S-1).
     * 학습 시뮬레이션에서 유저가 판단 시점 이후의 정답(주가 흐름)을 미리 볼 수 없도록 하는 것이 목적.
     */
    // 과거 확정 시세라 결과가 불변 → (종목, 기준일) 키로 캐시 (I-3)
    @Cacheable(cacheNames = CacheConfig.PRICE_HISTORY, key = "#stockCode + ':' + #asOfDate")
    @Transactional(readOnly = true)
    public StockPriceHistoryResponse getStockPriceHistoryUpTo(String stockCode, LocalDate asOfDate) {
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        return getHistoryResponse(stock, asOfDate);
    }

    @Cacheable(cacheNames = CacheConfig.PRICE_HISTORY_ALL, key = "#asOfDate.toString()")
    @Transactional(readOnly = true)
    public List<StockPriceHistoryResponse> getAllStocksPriceHistoryUpTo(LocalDate asOfDate) {
        return stockRepository.findAll().stream()
                .map(stock -> getHistoryResponse(stock, asOfDate))
                .collect(Collectors.toList());
    }

    private StockPriceHistoryResponse getHistoryResponse(Stock stock, LocalDate asOfDate) {
        // 기준일 이하만 조회 (최신순 N+1건) → 오름차순으로 뒤집는다.
        List<StockPrice> prices = new ArrayList<>(
                stockPriceRepository.findTop11ByStockAndPriceDateLessThanEqualOrderByPriceDateDesc(stock, asOfDate));
        Collections.reverse(prices);

        StockPriceHistoryResponse.StockPriceHistoryResponseBuilder base = StockPriceHistoryResponse.builder()
                .stockId(stock.getStockId())
                .stockCode(stock.getStockCode())
                .stockName(stock.getStockName())
                .sector(stock.getSector().name());

        if (prices.isEmpty()) {
            return base.prices(List.of()).build();
        }

        // 기준 종가: 리스트의 마지막(= 기준일 또는 그 직전 거래일) 종가
        BigDecimal baseClose = prices.get(prices.size() - 1).getClosePrice();

        // 리스트 첫 원소의 전일 종가 (변동률 계산용) — 기준일 이전 구간이므로 조회해도 무방
        BigDecimal earliestPrevClose = stockPriceRepository
                .findFirstByStockAndPriceDateBeforeOrderByPriceDateDesc(stock, prices.get(0).getPriceDate())
                .map(StockPrice::getClosePrice)
                .orElse(null);

        List<StockPriceHistoryItem> items = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            StockPrice current = prices.get(i);
            BigDecimal previousClose = (i > 0) ? prices.get(i - 1).getClosePrice() : earliestPrevClose;

            items.add(StockPriceHistoryItem.builder()
                    .date(current.getPriceDate())
                    .openPrice(current.getOpenPrice())
                    .closePrice(current.getClosePrice())
                    .highPrice(current.getHighPrice())
                    .lowPrice(current.getLowPrice())
                    .volume(current.getVolume())
                    .dailyChangeRate(current.calculateChangeRate(previousClose))
                    .baseChangeRate(current.calculateChangeRate(baseClose))
                    .isEventDate(current.getPriceDate().equals(asOfDate))
                    .build());
        }

        return base.prices(items).build();
    }
}
