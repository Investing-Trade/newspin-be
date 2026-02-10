package org.gp.newspinbe.domain.simulation.repository;

import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.simulation.domain.Portfolio;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    @Query("SELECT p FROM Portfolio p JOIN FETCH p.stock " +
            "WHERE p.session = :session AND p.quantity > 0 " +
            "ORDER BY p.stock.stockName ASC")
    List<Portfolio> findBySessionWithStock(@Param("session") SimulationSession session);

    Optional<Portfolio> findBySessionAndStock(SimulationSession session, Stock stock);

    @Query("SELECT COUNT(p) FROM Portfolio p WHERE p.session = :session AND p.quantity > 0")
    long countHoldingStocksBySession(@Param("session") SimulationSession session);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM Portfolio p " +
            "WHERE p.session = :session AND p.stock = :stock AND p.quantity > 0")
    boolean existsBySessionAndStockWithQuantity(
            @Param("session") SimulationSession session,
            @Param("stock") Stock stock);

    @Query("SELECT p.stock.sector, COUNT(p) FROM Portfolio p " +
            "WHERE p.session = :session AND p.quantity > 0 " +
            "GROUP BY p.stock.sector")
    List<Object[]> countBySectorForSession(@Param("session") SimulationSession session);

    @Modifying
    @Query("DELETE FROM Portfolio p WHERE p.quantity = 0")
    void deleteEmptyPortfolios();

    @Modifying
    @Query("DELETE FROM Portfolio p WHERE p.session = :session AND p.quantity = 0")
    void deleteEmptyPortfoliosBySession(@Param("session") SimulationSession session);

    @Modifying
    @Query("DELETE FROM Portfolio p WHERE p.session = :session")
    void deleteAllBySession(@Param("session") SimulationSession session);

    @Query("SELECT COALESCE(SUM(p.quantity), 0) FROM Portfolio p WHERE p.session = :session")
    long sumQuantityBySession(@Param("session") SimulationSession session);

    @Query("SELECT COALESCE(SUM(p.avgPurchasePrice * p.quantity), 0) " +
            "FROM Portfolio p WHERE p.session = :session")
    Double calculateTotalPurchaseValueBySession(@Param("session") SimulationSession session);
}
