package org.gp.newspinbe.domain.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.gp.newspinbe.domain.news.application.NewsService;
import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.domain.NewsSentiment;
import org.gp.newspinbe.domain.news.domain.UserNewsProgress;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.news.repository.UserNewsProgressRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * S-2. 개별 뉴스 감성 판단의 정오 이력이 남고, 리포트 채점용 기간 조회가 동작한다.
 * (이벤트 3건에만 의존하던 정답지를 일상 뉴스 판단까지 넓히는 입력.)
 */
@IntegrationTest
class NewsJudgmentAccuracyTest {

    @Autowired NewsService newsService;
    @Autowired NewsArticleRepository newsArticleRepository;
    @Autowired UserNewsProgressRepository progressRepository;
    @Autowired UserRepository userRepository;

    private NewsArticle news(LocalDate date) {
        return newsArticleRepository.save(NewsArticle.createNewsArticle(
                "판단테스트 뉴스 " + System.nanoTime(), "본문", date, "테스트"));
    }

    @Test
    void 감성_판단_결과가_기록되고_기간조회로_채점된다() {
        User user = userRepository.save(User.create("judge-" + System.nanoTime() + "@t.com", "x"));
        LocalDate d1 = LocalDate.parse("2019-03-04");
        LocalDate d2 = LocalDate.parse("2019-03-05");
        NewsArticle hit = news(d1);
        NewsArticle miss = news(d2);

        // 정답: 사용자 POSITIVE == AI POSITIVE
        newsService.recordNewsJudgment(user.getUserId(), hit.getNewsId(),
                NewsSentiment.POSITIVE, NewsSentiment.POSITIVE);
        // 오답: 사용자 POSITIVE != AI NEGATIVE
        newsService.recordNewsJudgment(user.getUserId(), miss.getNewsId(),
                NewsSentiment.POSITIVE, NewsSentiment.NEGATIVE);

        List<UserNewsProgress> judged = progressRepository.findJudgedByUserInPeriod(
                user.getUserId(), LocalDate.parse("2019-03-01"), LocalDate.parse("2019-03-31"));

        assertThat(judged).hasSize(2);
        assertThat(judged.stream().filter(UserNewsProgress::isCorrect).count()).isEqualTo(1);
        assertThat(judged.stream().allMatch(UserNewsProgress::isJudged)).isTrue();
    }

    @Test
    void 이미_판단한_뉴스를_다시_판단하면_행이_늘지_않고_갱신된다() {
        User user = userRepository.save(User.create("judge2-" + System.nanoTime() + "@t.com", "x"));
        NewsArticle article = news(LocalDate.parse("2019-04-10"));

        newsService.recordNewsJudgment(user.getUserId(), article.getNewsId(),
                NewsSentiment.NEGATIVE, NewsSentiment.POSITIVE); // 오답
        newsService.recordNewsJudgment(user.getUserId(), article.getNewsId(),
                NewsSentiment.POSITIVE, NewsSentiment.POSITIVE); // 재도전, 정답

        List<UserNewsProgress> judged = progressRepository.findJudgedByUserInPeriod(
                user.getUserId(), LocalDate.parse("2019-04-01"), LocalDate.parse("2019-04-30"));

        assertThat(judged).hasSize(1);
        assertThat(judged.get(0).isCorrect()).isTrue();
    }
}
