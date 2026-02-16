package org.gp.newspinbe.domain.news.repository;

import java.time.LocalDate;
import java.util.List;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    List<NewsArticle> findByArticleDate(LocalDate articleDate);

    // 특정 기간 내 이벤트 뉴스만 조회 (AI 보고서용)
    List<NewsArticle> findByArticleDateBetweenAndEventTypeIsNotNull(
            LocalDate startDate, LocalDate endDate);
}
