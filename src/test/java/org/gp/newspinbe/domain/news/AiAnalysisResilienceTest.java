package org.gp.newspinbe.domain.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.gp.newspinbe.domain.news.application.AIService;
import org.gp.newspinbe.domain.news.domain.NewsSentiment;
import org.gp.newspinbe.domain.news.dto.request.AIAnalysisRequest;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.news.repository.UserNewsProgressRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * R-1 / R-2. AI 서버(newspin-ai)가 죽어도:
 *  - LazyInitializationException 없이 (relatedStocks fetch-join)
 *  - 명시적 예외로 실패하고
 *  - 학습 완료로 기록되지 않는다 (markNewsAsLearned 이전에 실패).
 * 테스트 프로필의 ai-service.url 은 즉시 연결 거부되는 주소.
 */
@IntegrationTest
class AiAnalysisResilienceTest {

    @Autowired AIService aiService;
    @Autowired NewsArticleRepository newsArticleRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserNewsProgressRepository progressRepository;

    @Test
    void AI서버_장애시_명시적_예외이고_학습완료로_기록되지_않는다() {
        User user = userRepository.save(User.create("resil-" + System.nanoTime() + "@t.com", "x"));
        Long newsId = newsArticleRepository.findAll(PageRequest.of(0, 1)).getContent().get(0).getNewsId();

        AIAnalysisRequest request = new ObjectMapper().convertValue(
                Map.of("sentiment", NewsSentiment.POSITIVE, "reason", "판단 근거"), AIAnalysisRequest.class);

        assertThatThrownBy(() -> aiService.analyzeUserJudgment(user.getUserId(), newsId, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isIn(ErrorCode.AI_SERVICE_UNAVAILABLE, ErrorCode.EXTERNAL_SERVICE_ERROR);

        assertThat(progressRepository.existsByUser_UserIdAndNewsArticle_NewsId(user.getUserId(), newsId))
                .as("실패했으므로 학습 완료로 기록되면 안 된다")
                .isFalse();
    }
}
