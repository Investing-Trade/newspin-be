package org.gp.newspinbe.domain.news.repository;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.news.domain.UserNewsProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNewsProgressRepository extends JpaRepository<UserNewsProgress, Long> {

    long countByUser_UserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserNewsProgress unp WHERE unp.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    boolean existsByUser_UserIdAndNewsArticle_NewsId(Long userId, Long newsId);

    Optional<UserNewsProgress> findByUser_UserIdAndNewsArticle_NewsId(Long userId, Long newsId);

    /** 리포트 채점용 (S-2): 세션 기간 안에 발행된 뉴스에 대한 사용자의 감성 판단 이력. */
    @Query("SELECT p FROM UserNewsProgress p JOIN FETCH p.newsArticle n " +
            "WHERE p.user.userId = :userId AND p.userSentiment IS NOT NULL " +
            "AND n.articleDate BETWEEN :start AND :end " +
            "ORDER BY n.articleDate")
    List<UserNewsProgress> findJudgedByUserInPeriod(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
