package org.gp.newspinbe.domain.news.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    List<NewsArticle> findByArticleDate(LocalDate articleDate);

    /** relatedStocks 를 함께 로딩 — 트랜잭션 밖(외부 호출)에서 접근해도 안전하도록 (R-1). */
    @EntityGraph(attributePaths = "relatedStocks")
    Optional<NewsArticle> findWithRelatedStocksByNewsId(Long newsId);

    /** 아직 학습하지 않은 뉴스 수 (I-1: findAll 인메모리 필터 제거). */
    @Query("SELECT COUNT(n) FROM NewsArticle n WHERE NOT EXISTS " +
            "(SELECT 1 FROM UserNewsProgress p WHERE p.newsArticle = n AND p.user.userId = :userId)")
    long countUnlearnedByUser(@Param("userId") Long userId);

    /** 미학습 뉴스 중 offset 위치 1건 (Pageable 로 LIMIT/OFFSET). */
    @Query("SELECT n FROM NewsArticle n WHERE NOT EXISTS " +
            "(SELECT 1 FROM UserNewsProgress p WHERE p.newsArticle = n AND p.user.userId = :userId) " +
            "ORDER BY n.newsId")
    List<NewsArticle> findUnlearnedByUser(@Param("userId") Long userId, Pageable pageable);

    // 특정 기간 내 이벤트 뉴스만 조회 (AI 보고서용)
    List<NewsArticle> findByArticleDateBetweenAndEventTypeIsNotNull(
            LocalDate startDate, LocalDate endDate);
}
