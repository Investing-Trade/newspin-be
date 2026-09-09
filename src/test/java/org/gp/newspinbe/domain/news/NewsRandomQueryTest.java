package org.gp.newspinbe.domain.news;

import static org.assertj.core.api.Assertions.assertThat;

import org.gp.newspinbe.domain.news.application.NewsService;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManagerFactory;

/**
 * I-1. 미학습 뉴스 랜덤 조회가 전체 뉴스를 메모리에 올리지 않는지.
 * 기존 구현은 {@code findAll()} 로 시드 2,528건을 전부 로딩했다.
 */
@IntegrationTest
class NewsRandomQueryTest {

    @Autowired NewsService newsService;
    @Autowired UserRepository userRepository;
    @Autowired EntityManagerFactory emf;

    @Test
    void 랜덤_조회는_뉴스를_1건만_로딩한다() {
        User user = userRepository.save(User.create("news-q-" + System.nanoTime() + "@t.com", "x"));
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        newsService.getRandomUnlearnedNews(user.getUserId());

        assertThat(stats.getEntityLoadCount())
                .as("NewsArticle 엔티티 로드 수 (기존: 전체 2528)")
                .isLessThanOrEqualTo(1);
        assertThat(stats.getPrepareStatementCount())
                .as("실행 쿼리 수 (count + 1건 select)")
                .isLessThanOrEqualTo(3);
    }
}
