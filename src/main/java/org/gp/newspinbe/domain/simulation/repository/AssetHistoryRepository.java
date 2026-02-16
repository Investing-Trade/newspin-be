package org.gp.newspinbe.domain.simulation.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.simulation.domain.AssetHistory;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistory, Long> {

        List<AssetHistory> findBySessionOrderByRecordDateAsc(SimulationSession session);

        Optional<AssetHistory> findBySessionAndRecordDate(SimulationSession session, LocalDate recordDate);

        List<AssetHistory> findBySessionAndRecordDateBetweenOrderByRecordDateAsc(
                        SimulationSession session,
                        LocalDate startDate,
                        LocalDate endDate);

        Optional<AssetHistory> findFirstBySessionOrderByRecordDateDesc(SimulationSession session);

        Optional<AssetHistory> findFirstBySessionAndRecordDateLessThanOrderByRecordDateDesc(SimulationSession session,
                        LocalDate recordDate);

        List<AssetHistory> findBySessionAndRecordDateAfterOrderByRecordDateAsc(
                        SimulationSession session,
                        LocalDate afterDate);

        long countBySession(SimulationSession session);

        boolean existsBySessionAndRecordDate(SimulationSession session, LocalDate recordDate);

        @Query("SELECT MAX(ah.profitRate) FROM AssetHistory ah WHERE ah.session = :session")
        Double findMaxProfitRateBySession(@Param("session") SimulationSession session);

        @Query("SELECT MIN(ah.profitRate) FROM AssetHistory ah WHERE ah.session = :session")
        Double findMinProfitRateBySession(@Param("session") SimulationSession session);

        @Query("SELECT AVG(ah.totalAsset) FROM AssetHistory ah WHERE ah.session = :session")
        Double calculateAvgTotalAssetBySession(@Param("session") SimulationSession session);

        @Query("SELECT ah.recordDate, ah.totalAsset FROM AssetHistory ah " +
                        "WHERE ah.session = :session " +
                        "ORDER BY ah.recordDate ASC")
        List<Object[]> findTotalAssetTrendBySession(@Param("session") SimulationSession session);

        @Query("SELECT ah.recordDate, ah.profitRate FROM AssetHistory ah " +
                        "WHERE ah.session = :session " +
                        "ORDER BY ah.recordDate ASC")
        List<Object[]> findProfitRateTrendBySession(@Param("session") SimulationSession session);

        @Query("SELECT ah.recordDate, ah.cashBalance, ah.stockValue, ah.totalAsset " +
                        "FROM AssetHistory ah " +
                        "WHERE ah.session = :session " +
                        "ORDER BY ah.recordDate ASC")
        List<Object[]> findAssetCompositionTrendBySession(@Param("session") SimulationSession session);

        @Modifying
        @Query("DELETE FROM AssetHistory ah WHERE ah.session = :session")
        void deleteAllBySession(@Param("session") SimulationSession session);

        @Modifying
        @Query("DELETE FROM AssetHistory ah WHERE ah.recordDate < :beforeDate")
        void deleteByRecordDateBefore(@Param("beforeDate") LocalDate beforeDate);
}
