package org.gp.newspinbe.domain.event.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.event.domain.EventType;
import org.gp.newspinbe.domain.event.domain.MarketEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketEventRepository extends JpaRepository<MarketEvent, Long> {

    Optional<MarketEvent> findByEventDate(LocalDate eventDate);

    List<MarketEvent> findByEventDateBetween(LocalDate startDate, LocalDate endDate);

    List<MarketEvent> findByEventType(EventType eventType);

    List<MarketEvent> findByEventDateAfterOrderByEventDateAsc(LocalDate afterDate);

    boolean existsByEventDateBetween(LocalDate startDate, LocalDate endDate);

    Optional<MarketEvent> findFirstByOrderByEventDateDesc();

    List<MarketEvent> findByEventNameContaining(String keyword);

    @Query("SELECT e.eventType, COUNT(e) FROM MarketEvent e " +
            "WHERE e.eventDate BETWEEN :startDate AND :endDate " +
            "GROUP BY e.eventType")
    List<Object[]> countByEventTypeBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
