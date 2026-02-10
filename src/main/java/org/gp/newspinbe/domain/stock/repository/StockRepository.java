package org.gp.newspinbe.domain.stock.repository;

import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.domain.stock.domain.StockSector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByStockCode(String stockCode);

    List<Stock> findBySector(StockSector sector);

    List<Stock> findByStockNameContaining(String stockName);

    boolean existsByStockCode(String stockCode);

    List<Stock> findBySectorIn(List<StockSector> sectors);

    @Query("SELECT s.stockCode FROM Stock s")
    List<String> findAllStockCodes();

    @Query("SELECT s.sector, COUNT(s) FROM Stock s GROUP BY s.sector")
    List<Object[]> countBySector();
}
