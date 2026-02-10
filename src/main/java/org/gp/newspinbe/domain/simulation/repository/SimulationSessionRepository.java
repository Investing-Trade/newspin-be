package org.gp.newspinbe.domain.simulation.repository;

import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.simulation.domain.SessionStatus;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationSessionRepository extends JpaRepository<SimulationSession, Long> {

    List<SimulationSession> findByUserOrderByCreatedAtDesc(User user);

    List<SimulationSession> findByUserAndStatus(User user, SessionStatus status);

    @Query("SELECT s FROM SimulationSession s WHERE s.user = :user AND s.status = 'ACTIVE'")
    List<SimulationSession> findActiveSessionsByUser(@Param("user") User user);

    @Query("SELECT s FROM SimulationSession s WHERE s.user = :user AND s.status = 'COMPLETED' " +
            "ORDER BY s.createdAt DESC")
    List<SimulationSession> findCompletedSessionsByUser(@Param("user") User user);

    Optional<SimulationSession> findFirstByUserOrderByCreatedAtDesc(User user);

    List<SimulationSession> findByStatus(SessionStatus status);

    long countByUser(User user);

    long countByUserAndStatus(User user, SessionStatus status);

    boolean existsByUserAndSessionId(User user, Long sessionId);

    Optional<SimulationSession> findBySessionIdAndUser(Long sessionId, User user);

    @Query("SELECT AVG((s.currentCapital - s.initialCapital) / s.initialCapital * 100) " +
            "FROM SimulationSession s " +
            "WHERE s.user = :user AND s.status = 'COMPLETED'")
    Double calculateAvgProfitRateByUser(@Param("user") User user);
}
