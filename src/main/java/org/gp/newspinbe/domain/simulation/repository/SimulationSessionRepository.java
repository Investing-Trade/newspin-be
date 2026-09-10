package org.gp.newspinbe.domain.simulation.repository;

import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.simulation.domain.SessionStatus;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface SimulationSessionRepository extends JpaRepository<SimulationSession, Long> {

    /**
     * 세션 잔고·진행일을 바꾸는 작업(거래, 다음날 진행)에서 사용. 같은 세션에 대한 동시 쓰기를
     * 직렬화해 잔고 lost update(R-4)와 AssetHistory 유니크 위반(R-3)을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SimulationSession s WHERE s.sessionId = :sessionId")
    Optional<SimulationSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    List<SimulationSession> findByUserOrderByCreatedAtDesc(User user);

    org.springframework.data.domain.Page<SimulationSession> findByUser(
            User user, org.springframework.data.domain.Pageable pageable);

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
