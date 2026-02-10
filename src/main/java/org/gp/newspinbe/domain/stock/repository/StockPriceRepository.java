package org.gp.newspinbe.domain.stock.repository;

import java.time.LocalDate;
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

    List<StockPrice> findByStockAndPriceDateBetween(Stock stock, LocalDate startDate, LocalDate endDate);

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
