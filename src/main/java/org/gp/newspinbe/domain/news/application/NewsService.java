package org.gp.newspinbe.domain.news.application;

import java.util.Random;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.domain.NewsSentiment;
import org.gp.newspinbe.domain.news.domain.UserNewsProgress;
import org.gp.newspinbe.domain.news.dto.response.NewsResponse;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.news.repository.UserNewsProgressRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
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

    /**
     * 미학습 뉴스 1건 랜덤 반환. 모두 학습했으면 진행률을 리셋하고 전체에서 뽑는다.
     *
     * <p>기존: {@code findAll()} 로 전체 뉴스(TEXT 본문 포함)를 메모리에 올린 뒤 인메모리 필터링.
     * 개선: count 쿼리 + offset 1건 조회 (I-1).
     */
    @Transactional
    public NewsResponse getRandomUnlearnedNews(Long userId) {
        long unlearned = newsArticleRepository.countUnlearnedByUser(userId);

        if (unlearned == 0) {
            long total = newsArticleRepository.count();
            if (total == 0) {
                throw new CustomException(ErrorCode.NEWS_NOT_FOUND);
            }
            resetUserProgress(userId);
            unlearned = total;
        }

        int offset = random.nextInt((int) unlearned);
        NewsArticle selected = newsArticleRepository
                .findUnlearnedByUser(userId, PageRequest.of(offset, 1))
                .stream().findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.NEWS_NOT_FOUND));

        return NewsResponse.from(selected);
    }

    /**
     * 뉴스 학습 완료 + 감성 판단 결과 기록 (S-2).
     * 이미 학습한 뉴스면 최신 판단으로 갱신하고, 아니면 새로 만든다.
     */
    @Transactional
    public void recordNewsJudgment(Long userId, Long newsId,
            NewsSentiment userSentiment, NewsSentiment aiSentiment) {
        UserNewsProgress existing = userNewsProgressRepository
                .findByUser_UserIdAndNewsArticle_NewsId(userId, newsId)
                .orElse(null);
        if (existing != null) {
            existing.recordJudgment(userSentiment, aiSentiment);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        NewsArticle newsArticle = newsArticleRepository.findById(newsId)
                .orElseThrow(() -> new CustomException(ErrorCode.NEWS_NOT_FOUND));

        userNewsProgressRepository.save(
                UserNewsProgress.withJudgment(user, newsArticle, userSentiment, aiSentiment));
    }

    @Transactional
    public void resetUserProgress(Long userId) {
        userNewsProgressRepository.deleteAllByUserId(userId);
    }
}
