package org.gp.newspinbe.domain.stock.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.domain.StockPrice;
import org.gp.newspinbe.domain.stock.repository.StockPriceRepository;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "특정 시점 기준 종가" 조회를 한 곳에서 처리한다 (R-5).
 *
 * <p>이전에는 시세 결측 시 서비스마다 제각각(조용히 0 / 직전 영업일 폴백 / 예외)이라,
 * 자산이 실제보다 낮게 계산되어도 감지할 수 없었다. 이제 결측·폴백을
 * {@code newspin.stock.price.lookup} 메트릭과 WARN 로그로 드러낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockPriceResolver {

    private final StockPriceRepository stockPriceRepository;
    private final MeterRegistry meterRegistry;

    /** 기준일 종가, 없으면 직전 영업일 종가. 데이터가 아예 없으면 empty. */
    public Optional<BigDecimal> resolveCloseAsOf(Stock stock, LocalDate asOf) {
        Optional<BigDecimal> exact = stockPriceRepository.findByStockAndPriceDate(stock, asOf)
                .map(StockPrice::getClosePrice);
        if (exact.isPresent()) {
            count("exact");
            return exact;
        }

        Optional<BigDecimal> previous = stockPriceRepository
                .findFirstByStockAndPriceDateBeforeOrderByPriceDateDesc(stock, asOf)
                .map(StockPrice::getClosePrice);
        if (previous.isPresent()) {
            count("fallback");
            log.debug("시세 결측 - {} {} → 직전 영업일 종가 사용", stock.getStockCode(), asOf);
            return previous;
        }

        count("missing");
        log.warn("시세 없음 - {} {} 기준으로 조회 가능한 종가가 전혀 없음", stock.getStockCode(), asOf);
        return Optional.empty();
    }

    /** 평가용 — 결측이면 0. 결측 자체는 위 메트릭/로그로 이미 드러난다. */
    public BigDecimal closeForValuation(Stock stock, LocalDate asOf) {
        return resolveCloseAsOf(stock, asOf).orElse(BigDecimal.ZERO);
    }

    /**
     * 여러 종목의 평가용 종가를 한 번에 (I-2). 종목 수와 무관하게 쿼리 1~2회.
     * 반환 맵은 요청한 모든 stockId 를 키로 가진다 (결측이면 0).
     */
    public Map<Long, BigDecimal> closesForValuation(Collection<Stock> stocks, LocalDate asOf) {
        if (stocks == null || stocks.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = stocks.stream().map(Stock::getStockId).distinct().toList();
        Map<Long, BigDecimal> byStockId = new HashMap<>();

        for (StockPrice p : stockPriceRepository.findByStockIdsAndPriceDate(ids, asOf)) {
            byStockId.put(p.getStock().getStockId(), p.getClosePrice());
        }
        countN("exact", byStockId.size());

        List<Long> missing = ids.stream().filter(id -> !byStockId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            int before = byStockId.size();
            for (StockPrice p : stockPriceRepository.findLatestBeforeByStockIds(missing, asOf)) {
                byStockId.putIfAbsent(p.getStock().getStockId(), p.getClosePrice());
            }
            countN("fallback", byStockId.size() - before);
        }

        int stillMissing = 0;
        for (Long id : ids) {
            if (!byStockId.containsKey(id)) {
                byStockId.put(id, BigDecimal.ZERO);
                stillMissing++;
            }
        }
        if (stillMissing > 0) {
            countN("missing", stillMissing);
            log.warn("시세 없음 - {}건 (asOf={})", stillMissing, asOf);
        }
        return byStockId;
    }

    private void count(String result) {
        countN(result, 1);
    }

    private void countN(String result, int n) {
        if (n > 0) {
            meterRegistry.counter("newspin.stock.price.lookup", "result", result).increment(n);
        }
    }
}
