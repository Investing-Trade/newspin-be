package org.gp.newspinbe.domain.stock.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.domain.StockPrice;
import org.gp.newspinbe.domain.stock.dto.response.StockPriceHistoryItem;
import org.gp.newspinbe.domain.stock.dto.response.StockPriceHistoryResponse;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.gp.newspinbe.domain.stock.repository.StockRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;

    @Transactional(readOnly = true)
    public StockPriceHistoryResponse getStockPriceHistoryAroundDate(String stockCode, LocalDate targetDate) {
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));

        return getHistoryResponse(stock, targetDate);
    }

    @Transactional(readOnly = true)
    public List<StockPriceHistoryResponse> getAllStocksPriceHistoryAroundDate(LocalDate targetDate) {
        List<Stock> stocks = stockRepository.findAll();
        return stocks.stream()
                .map(stock -> getHistoryResponse(stock, targetDate))
                .collect(Collectors.toList());
    }

    private StockPriceHistoryResponse getHistoryResponse(Stock stock, LocalDate targetDate) {
        // 1. 이전 5영업일 조회 (내림차순 정렬되어 있으므로 오름차순으로 뒤집어야 함)
        List<StockPrice> beforePrices = stockPriceRepository.findTop5ByStockAndPriceDateLessThanOrderByPriceDateDesc(stock, targetDate);
        List<StockPrice> beforePricesChronological = new ArrayList<>(beforePrices);
        Collections.reverse(beforePricesChronological);

        // 2. 기준일 당일 주가 조회
        Optional<StockPrice> targetPriceOpt = stockPriceRepository.findByStockAndPriceDate(stock, targetDate);

        // 3. 이후 5영업일 조회 (오름차순 정렬)
        List<StockPrice> afterPrices = stockPriceRepository.findTop5ByStockAndPriceDateGreaterThanOrderByPriceDateAsc(stock, targetDate);

        // 4. 시간순으로 주가 병합
        List<StockPrice> allPrices = new ArrayList<>();
        allPrices.addAll(beforePricesChronological);
        targetPriceOpt.ifPresent(allPrices::add);
        allPrices.addAll(afterPrices);

        // 5. 기준일의 종가 결정 (변동률 기준값)
        BigDecimal targetClosePrice = targetPriceOpt.map(StockPrice::getClosePrice).orElse(null);
        if (targetClosePrice == null && !beforePricesChronological.isEmpty()) {
            targetClosePrice = beforePricesChronological.get(beforePricesChronological.size() - 1).getClosePrice();
        }
        if (targetClosePrice == null && !afterPrices.isEmpty()) {
            targetClosePrice = afterPrices.get(0).getClosePrice();
        }

        // 6. DTO 변환 및 변동률 계산
        List<StockPriceHistoryItem> items = new ArrayList<>();
        for (int i = 0; i < allPrices.size(); i++) {
            StockPrice current = allPrices.get(i);

            // 전일 대비 변동률 (dailyChangeRate) 계산을 위한 전일 종가 조회
            BigDecimal previousClose = null;
            if (i > 0) {
                previousClose = allPrices.get(i - 1).getClosePrice();
            } else {
                // 병합 리스트의 첫 번째 원소인 경우, DB에서 해당 날짜 바로 이전 1개의 데이터를 추가 조회
                Optional<StockPrice> actualPrev = stockPriceRepository.findTop5ByStockAndPriceDateLessThanOrderByPriceDateDesc(stock, current.getPriceDate())
                        .stream().findFirst();
                if (actualPrev.isPresent()) {
                    previousClose = actualPrev.get().getClosePrice();
                }
            }

            double dailyChangeRate = current.calculateChangeRate(previousClose);
            double baseChangeRate = current.calculateChangeRate(targetClosePrice);
            boolean isEventDate = current.getPriceDate().equals(targetDate);

            items.add(StockPriceHistoryItem.builder()
                    .date(current.getPriceDate())
                    .openPrice(current.getOpenPrice())
                    .closePrice(current.getClosePrice())
                    .highPrice(current.getHighPrice())
                    .lowPrice(current.getLowPrice())
                    .volume(current.getVolume())
                    .dailyChangeRate(dailyChangeRate)
                    .baseChangeRate(baseChangeRate)
                    .isEventDate(isEventDate)
                    .build());
        }

        return StockPriceHistoryResponse.builder()
                .stockId(stock.getStockId())
                .stockCode(stock.getStockCode())
                .stockName(stock.getStockName())
                .sector(stock.getSector().name())
                .prices(items)
                .build();
    }
}
