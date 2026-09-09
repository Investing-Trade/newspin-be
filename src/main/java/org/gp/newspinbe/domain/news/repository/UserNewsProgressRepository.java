package org.gp.newspinbe.domain.news.repository;


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
}
