package org.gp.newspinbe.domain.news.application;

import java.util.List;
import java.util.Random;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.domain.UserNewsProgress;
import org.gp.newspinbe.domain.news.dto.response.NewsResponse;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.news.repository.UserNewsProgressRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsArticleRepository newsArticleRepository;
    private final UserNewsProgressRepository userNewsProgressRepository;
    private final UserRepository userRepository;
    private final Random random = new Random();

    @Transactional(readOnly = true)
    public NewsResponse getRandomUnlearnedNews(Long userId) {
        List<Long> learnedNewsIds = userNewsProgressRepository.findLearnedNewsIdsByUserId(userId);
        List<NewsArticle> allNews = newsArticleRepository.findAll();

        if (allNews.isEmpty()) {
            throw new CustomException(ErrorCode.NEWS_NOT_FOUND);
        }

        List<NewsArticle> unlearnedNews = allNews.stream()
                .filter(news -> !learnedNewsIds.contains(news.getNewsId()))
                .toList();

        // 모든 뉴스를 학습한 경우, 진행률 초기화
        if (unlearnedNews.isEmpty()) {
            resetUserProgress(userId);
            unlearnedNews = allNews;
        }

        // 랜덤으로 1개 선택
        int randomIndex = random.nextInt(unlearnedNews.size());
        NewsArticle selectedNews = unlearnedNews.get(randomIndex);

        return NewsResponse.from(selectedNews);
    }

    @Transactional
    public void markNewsAsLearned(Long userId, Long newsId) {
        if (userNewsProgressRepository.existsByUser_UserIdAndNewsArticle_NewsId(userId, newsId)) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        NewsArticle newsArticle = newsArticleRepository.findById(newsId)
                .orElseThrow(() -> new CustomException(ErrorCode.NEWS_NOT_FOUND));

        UserNewsProgress progress = UserNewsProgress.createUserNewsProgress(user, newsArticle);
        userNewsProgressRepository.save(progress);
    }

    @Transactional
    public void resetUserProgress(Long userId) {
        userNewsProgressRepository.deleteAllByUserId(userId);
    }
}
