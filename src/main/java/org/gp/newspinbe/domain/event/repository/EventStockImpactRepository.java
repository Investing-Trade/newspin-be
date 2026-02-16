package org.gp.newspinbe.domain.event.repository;

import java.util.List;

import org.gp.newspinbe.domain.event.domain.EventStockImpact;
import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventStockImpactRepository extends JpaRepository<EventStockImpact, Long> {

        // 특정 이벤트 뉴스의 종목별 영향도 조회
        @Query("SELECT esi FROM EventStockImpact esi " +
                        "JOIN FETCH esi.stock " +
                        "WHERE esi.newsArticle = :newsArticle")
        List<EventStockImpact> findByNewsArticleWithStock(@Param("newsArticle") NewsArticle newsArticle);

        // 특정 종목에 영향을 미치는 이벤트 뉴스 조회
        @Query("SELECT esi FROM EventStockImpact esi " +
                        "JOIN FETCH esi.newsArticle " +
                        "WHERE esi.stock = :stock")
        List<EventStockImpact> findByStockWithNewsArticle(@Param("stock") Stock stock);

        // 유의미한 영향도만 조회 (절대값 5% 이상)
        @Query("SELECT esi FROM EventStockImpact esi " +
                        "JOIN FETCH esi.stock " +
                        "WHERE esi.newsArticle = :newsArticle " +
                        "AND (esi.impactRate >= 5.0 OR esi.impactRate <= -5.0)")
        List<EventStockImpact> findSignificantImpactsByNewsArticle(@Param("newsArticle") NewsArticle newsArticle);

        // 영향도 크기순 정렬 조회
        @Query("SELECT esi FROM EventStockImpact esi " +
                        "JOIN FETCH esi.stock " +
                        "WHERE esi.newsArticle = :newsArticle " +
                        "ORDER BY ABS(esi.impactRate) DESC")
        List<EventStockImpact> findTopImpactsByNewsArticle(@Param("newsArticle") NewsArticle newsArticle);

        boolean existsByNewsArticleAndStock(NewsArticle newsArticle, Stock stock);

        // 여러 이벤트 뉴스의 영향도 일괄 조회 (AI 보고서용)
        @Query("SELECT esi FROM EventStockImpact esi " +
                        "JOIN FETCH esi.stock " +
                        "JOIN FETCH esi.newsArticle " +
                        "WHERE esi.newsArticle IN :newsArticles")
        List<EventStockImpact> findByNewsArticles(@Param("newsArticles") List<NewsArticle> newsArticles);
}
