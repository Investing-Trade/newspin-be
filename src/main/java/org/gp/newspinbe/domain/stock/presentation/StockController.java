package org.gp.newspinbe.domain.stock.presentation;

import java.time.LocalDate;
import java.util.List;

import org.gp.newspinbe.domain.stock.application.StockService;
import org.gp.newspinbe.domain.stock.dto.response.StockPriceHistoryResponse;
import org.gp.newspinbe.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    // 특정 종목의 기준일까지의 주가 히스토리 (기준일 이후 시세는 반환하지 않음 — C-5)
    @GetMapping("/{stockCode}/price-range")
    public ResponseEntity<ApiResponse<StockPriceHistoryResponse>> getStockPriceHistoryRange(
            @PathVariable String stockCode,
            @RequestParam LocalDate date) {
        StockPriceHistoryResponse response = stockService.getStockPriceHistoryUpTo(stockCode, date);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 전체 종목의 기준일까지의 주가 히스토리 (비교용)
    @GetMapping("/price-range")
    public ResponseEntity<ApiResponse<List<StockPriceHistoryResponse>>> getAllStocksPriceHistoryRange(
            @RequestParam LocalDate date) {
        List<StockPriceHistoryResponse> response = stockService.getAllStocksPriceHistoryUpTo(date);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
