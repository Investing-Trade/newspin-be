package org.gp.newspinbe.domain.news.repository;

import java.util.List;

import org.gp.newspinbe.domain.news.domain.UserNewsProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNewsProgressRepository extends JpaRepository<UserNewsProgress, Long> {

    @Query("SELECT unp.newsArticle.newsId FROM UserNewsProgress unp WHERE unp.user.userId = :userId")
    List<Long> findLearnedNewsIdsByUserId(@Param("userId") Long userId);

    long countByUser_UserId(Long userId);

    @Modifying
    @Query("DELETE FROM UserNewsProgress unp WHERE unp.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    boolean existsByUser_UserIdAndNewsArticle_NewsId(Long userId, Long newsId);
}
