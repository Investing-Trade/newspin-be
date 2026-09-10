package org.gp.newspinbe.domain.simulation.repository;

import java.time.LocalDate;
import java.util.List;

import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.domain.Trade;
import org.gp.newspinbe.domain.simulation.domain.TradeType;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findBySessionOrderByCreatedAtAsc(SimulationSession session);

    /** 거래 내역 페이지 조회 (I-10). stock 을 함께 로딩해 매핑 시 N+1 방지. */
    @EntityGraph(attributePaths = "stock")
    Page<Trade> findBySession(SimulationSession session, Pageable pageable);

    @Query("SELECT t FROM Trade t JOIN FETCH t.stock " +
            "WHERE t.session = :session AND t.tradeDate = :tradeDate " +
            "ORDER BY t.createdAt ASC")
    List<Trade> findBySessionAndTradeDateWithStock(
            @Param("session") SimulationSession session,
            @Param("tradeDate") LocalDate tradeDate);

    @Query("SELECT t FROM Trade t JOIN FETCH t.stock " +
            "WHERE t.session = :session " +
            "AND t.tradeDate BETWEEN :startDate AND :endDate " +
            "ORDER BY t.tradeDate ASC, t.createdAt ASC")
    List<Trade> findBySessionAndTradeDateBetweenWithStock(
            @Param("session") SimulationSession session,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM Trade t " +
            "WHERE t.session = :session AND t.stock = :stock " +
            "ORDER BY t.tradeDate ASC")
    List<Trade> findBySessionAndStock(
            @Param("session") SimulationSession session,
            @Param("stock") Stock stock);

    List<Trade> findBySessionAndTradeType(SimulationSession session, TradeType tradeType);

    long countBySession(SimulationSession session);

    long countBySessionAndTradeType(SimulationSession session, TradeType tradeType);

    long countBySessionAndStock(SimulationSession session, Stock stock);

    @Query("SELECT t.stock.stockName, COUNT(t) FROM Trade t " +
            "WHERE t.session = :session " +
            "GROUP BY t.stock.stockName " +
            "ORDER BY COUNT(t) DESC")
    List<Object[]> countByStockForSession(@Param("session") SimulationSession session);

    @Query("SELECT t.stock.sector, COUNT(t) FROM Trade t " +
            "WHERE t.session = :session " +
            "GROUP BY t.stock.sector " +
            "ORDER BY COUNT(t) DESC")
    List<Object[]> countBySectorForSession(@Param("session") SimulationSession session);

    @Query("SELECT AVG(t.totalAmount) FROM Trade t WHERE t.session = :session")
    Double calculateAvgTradeAmountBySession(@Param("session") SimulationSession session);

    @Query("SELECT COALESCE(SUM(t.totalAmount), 0) FROM Trade t " +
            "WHERE t.session = :session AND t.tradeType = 'BUY'")
    Double calculateTotalBuyAmountBySession(@Param("session") SimulationSession session);

    @Query("SELECT COALESCE(SUM(t.totalAmount), 0) FROM Trade t " +
            "WHERE t.session = :session AND t.tradeType = 'SELL'")
    Double calculateTotalSellAmountBySession(@Param("session") SimulationSession session);
}
