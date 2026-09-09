package org.gp.newspinbe.domain.news.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    List<NewsArticle> findByArticleDate(LocalDate articleDate);

    /** relatedStocks 를 함께 로딩 — 트랜잭션 밖(외부 호출)에서 접근해도 안전하도록 (R-1). */
    @EntityGraph(attributePaths = "relatedStocks")
    Optional<NewsArticle> findWithRelatedStocksByNewsId(Long newsId);

    // 특정 기간 내 이벤트 뉴스만 조회 (AI 보고서용)
    List<NewsArticle> findByArticleDateBetweenAndEventTypeIsNotNull(
            LocalDate startDate, LocalDate endDate);
}
