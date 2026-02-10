package org.gp.newspinbe.domain.event.repository;

import java.util.List;

import org.gp.newspinbe.domain.event.domain.EventStockImpact;
import org.gp.newspinbe.domain.event.domain.MarketEvent;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventStockImpactRepository extends JpaRepository<EventStockImpact, Long> {

    @Query("SELECT esi FROM EventStockImpact esi " +
            "JOIN FETCH esi.stock " +
            "WHERE esi.event = :event")
    List<EventStockImpact> findByEventWithStock(@Param("event") MarketEvent event);

    @Query("SELECT esi FROM EventStockImpact esi " +
            "JOIN FETCH esi.event " +
            "WHERE esi.stock = :stock")
    List<EventStockImpact> findByStockWithEvent(@Param("stock") Stock stock);

    @Query("SELECT esi FROM EventStockImpact esi " +
            "JOIN FETCH esi.stock " +
            "WHERE esi.event = :event " +
            "AND (esi.impactRate >= 5.0 OR esi.impactRate <= -5.0)")
    List<EventStockImpact> findSignificantImpactsByEvent(@Param("event") MarketEvent event);

    @Query("SELECT esi FROM EventStockImpact esi " +
            "JOIN FETCH esi.stock " +
            "WHERE esi.event = :event " +
            "ORDER BY ABS(esi.impactRate) DESC")
    List<EventStockImpact> findTopImpactsByEvent(@Param("event") MarketEvent event);

    @Query("SELECT esi FROM EventStockImpact esi " +
            "JOIN FETCH esi.stock " +
            "WHERE esi.event = :event AND esi.impactRate > 0 " +
            "ORDER BY esi.impactRate DESC")
    List<EventStockImpact> findPositiveImpactsByEvent(@Param("event") MarketEvent event);

    @Query("SELECT esi FROM EventStockImpact esi " +
            "JOIN FETCH esi.stock " +
            "WHERE esi.event = :event AND esi.impactRate < 0 " +
            "ORDER BY esi.impactRate ASC")
    List<EventStockImpact> findNegativeImpactsByEvent(@Param("event") MarketEvent event);

    boolean existsByEventAndStock(MarketEvent event, Stock stock);

    @Query("SELECT esi FROM EventStockImpact esi " +
            "JOIN FETCH esi.stock " +
            "JOIN FETCH esi.event " +
            "WHERE esi.event IN :events")
    List<EventStockImpact> findByEvents(@Param("events") List<MarketEvent> events);
}
