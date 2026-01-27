package org.gp.newspinbe.domain.news.application;

import java.util.Random;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.domain.NewsSentiment;
import org.gp.newspinbe.domain.news.dto.request.AIAnalysisRequest;
import org.gp.newspinbe.domain.news.dto.response.AIAnalysisResponse;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AIService {

        private final NewsArticleRepository newsArticleRepository;
        private final NewsService newsService;
        private final Random random = new Random();

        @Transactional
        public AIAnalysisResponse analyzeUserJudgment(Long userId, Long newsId, AIAnalysisRequest aiAnalysisRequest) {
                NewsArticle newsArticle = newsArticleRepository.findById(newsId)
                                .orElseThrow(() -> new CustomException(ErrorCode.NEWS_NOT_FOUND));

                // TODO: 현재는 임시 응답을 반환, 향후 실제 AI 서비스와 연동 필요
                AIAnalysisResponse aiAnalysisResponse = generateMockAIResponse(aiAnalysisRequest.getSentiment(),
                                aiAnalysisRequest.getReason(), newsArticle);

                newsService.markNewsAsLearned(userId, newsId);

                return aiAnalysisResponse;
        }

        /**
         * [임시 구현] Mock AI 응답 생성
         * 실제 AI 서비스 연동 시 이 메서드를 대체하면 됩니다.
         */
        // TODO: Mock AI 응답 생성 - 실제 AI 서비스 연동 필요
        private AIAnalysisResponse generateMockAIResponse(NewsSentiment userSentiment, String userReason,
                        NewsArticle newsArticle) {
                // 랜덤으로 AI의 판단 결정 (50% 확률로 사용자와 동일)
                boolean agreeWithUser = random.nextBoolean();
                NewsSentiment aiSentiment = agreeWithUser ? userSentiment
                                : (userSentiment == NewsSentiment.POSITIVE ? NewsSentiment.NEGATIVE
                                                : NewsSentiment.POSITIVE);

                boolean isCorrect = userSentiment == aiSentiment;

                String feedback;
                if (isCorrect) {
                        feedback = String.format(
                                        "정확한 분석입니다! 이 뉴스는 %s로 판단됩니다. " +
                                                        "사용자께서 '%s'라고 분석하신 이유가 타당합니다. " +
                                                        "특히 뉴스의 핵심 내용을 잘 파악하셨습니다. " +
                                                        "(※ 현재는 AI 분석 기능이 준비 중이며, 임시 피드백이 제공되고 있습니다.)",
                                        aiSentiment == NewsSentiment.POSITIVE ? "호재" : "악재",
                                        userReason);
                } else {
                        feedback = String.format(
                                        "이 뉴스는 %s로 판단하는 것이 더 적절합니다. " +
                                                        "사용자께서는 %s로 분석하셨는데, 뉴스의 장기적 영향과 시장 반응을 고려하면 " +
                                                        "다른 관점에서 해석할 필요가 있습니다. " +
                                                        "뉴스 본문의 주요 키워드와 문맥을 다시 한번 검토해보세요. " +
                                                        "(※ 현재는 AI 분석 기능이 준비 중이며, 임시 피드백이 제공되고 있습니다.)",
                                        aiSentiment == NewsSentiment.POSITIVE ? "호재" : "악재",
                                        userSentiment == NewsSentiment.POSITIVE ? "호재" : "악재");
                }

                return AIAnalysisResponse.builder()
                                .aiSentiment(aiSentiment)
                                .aiFeedback(feedback)
                                .isCorrect(isCorrect)
                                .build();
        }
}
