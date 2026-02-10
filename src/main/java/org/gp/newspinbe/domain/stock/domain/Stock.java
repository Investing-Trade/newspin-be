package org.gp.newspinbe.domain.stock.domain;

import org.gp.newspinbe.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주식 종목 정보
 * market 필드 제거 (KOSPI/KOSDAQ 구분 불필요)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock", indexes = {
        @Index(name = "idx_stock_code", columnList = "stock_code"),
        @Index(name = "idx_stock_sector", columnList = "sector")
})
public class Stock extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    @Column(name = "stock_code", nullable = false, unique = true, length = 10)
    private String stockCode; // 종목 코드 (예: "005930")

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName; // 종목명 (예: "삼성전자")

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockSector sector; // 업종 (6가지 중 1개)

    @Column(columnDefinition = "TEXT")
    private String description; // 종목 설명 (선택)

    private Stock(String stockCode, String stockName, StockSector sector, String description) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.sector = sector;
        this.description = description;
    }

    public static Stock createStock(String stockCode, String stockName, StockSector sector) {
        return new Stock(stockCode, stockName, sector, null);
    }

    public static Stock createStock(String stockCode, String stockName, StockSector sector, String description) {
        return new Stock(stockCode, stockName, sector, description);
    }

    public void updateDescription(String description) {
        this.description = description;
    }
}
