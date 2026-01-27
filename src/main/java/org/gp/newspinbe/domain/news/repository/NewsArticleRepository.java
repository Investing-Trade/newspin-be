package org.gp.newspinbe.domain.news.repository;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
}
