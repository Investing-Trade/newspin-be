package org.gp.newspinbe.domain.stock.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.domain.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    Optional<StockPrice> findByStockAndPriceDate(Stock stock, LocalDate priceDate);

    Optional<StockPrice> findFirstByStockAndPriceDateBeforeOrderByPriceDateDesc(Stock stock, LocalDate priceDate);
    
    List<StockPrice> findByStockAndPriceDateBetween(Stock stock, LocalDate startDate, LocalDate endDate);

    List<StockPrice> findTop5ByStockAndPriceDateLessThanOrderByPriceDateDesc(Stock stock, LocalDate priceDate);

    List<StockPrice> findTop5ByStockAndPriceDateGreaterThanOrderByPriceDateAsc(Stock stock, LocalDate priceDate);

    /** 기준일(포함) 이하의 최근 시세. 미래 시세 노출 금지 (C-5). */
    List<StockPrice> findTop11ByStockAndPriceDateLessThanEqualOrderByPriceDateDesc(Stock stock, LocalDate asOfDate);

    /** 여러 종목의 기준일 종가를 한 번에 (I-2: 종목별 반복 조회 제거). */
    @Query("SELECT sp FROM StockPrice sp JOIN FETCH sp.stock " +
            "WHERE sp.stock.stockId IN :stockIds AND sp.priceDate = :date")
    List<StockPrice> findByStockIdsAndPriceDate(@Param("stockIds") Collection<Long> stockIds,
            @Param("date") LocalDate date);

    /** 여러 종목의 '기준일 직전' 종가 (종목별 최신 1건). */
    @Query("SELECT sp FROM StockPrice sp JOIN FETCH sp.stock " +
            "WHERE sp.stock.stockId IN :stockIds AND sp.priceDate = " +
            "(SELECT MAX(sp2.priceDate) FROM StockPrice sp2 " +
            " WHERE sp2.stock = sp.stock AND sp2.priceDate < :date)")
    List<StockPrice> findLatestBeforeByStockIds(@Param("stockIds") Collection<Long> stockIds,
            @Param("date") LocalDate date);

    @Query("SELECT sp FROM StockPrice sp JOIN FETCH sp.stock WHERE sp.priceDate = :priceDate")
    List<StockPrice> findAllByPriceDateWithStock(@Param("priceDate") LocalDate priceDate);

    Optional<StockPrice> findFirstByStockOrderByPriceDateDesc(Stock stock);

    @Query("SELECT sp FROM StockPrice sp JOIN FETCH sp.stock " +
            "WHERE sp.priceDate BETWEEN :startDate AND :endDate " +
            "ORDER BY sp.priceDate ASC, sp.stock.stockId ASC")
    List<StockPrice> findAllByPriceDateBetweenWithStock(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    boolean existsByStockAndPriceDate(Stock stock, LocalDate priceDate);

    @Query("SELECT COUNT(sp) FROM StockPrice sp WHERE sp.priceDate >= :startDate")
    long countByPriceDateAfter(@Param("startDate") LocalDate startDate);

    long countByStock(Stock stock);
}
