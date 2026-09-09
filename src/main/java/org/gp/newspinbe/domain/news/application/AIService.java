package org.gp.newspinbe.domain.news.application;

import java.util.UUID;

import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.domain.NewsSentiment;
import org.gp.newspinbe.domain.news.dto.request.AIAnalysisRequest;
import org.gp.newspinbe.domain.news.dto.request.ExternalAIRequest;
import org.gp.newspinbe.domain.news.dto.response.AIAnalysisResponse;
import org.gp.newspinbe.domain.news.dto.response.ExternalAIResponse;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.gp.newspinbe.global.service.GeminiService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 뉴스 감정 판단 학습 플로우.
 *
 * <p>트랜잭션을 열지 않는다 (R-1). 뉴스 로딩은 리포지토리가 자체 트랜잭션으로 처리하고,
 * 외부 호출(newspin-ai, Gemini)은 트랜잭션 밖에서 실행해 DB 커넥션을 점유하지 않는다.
 * 학습 완료 기록만 {@link NewsService#markNewsAsLearned} 의 트랜잭션으로 커밋한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final NewsArticleRepository newsArticleRepository;
    private final NewsService newsService;
    private final AiAnalysisClient aiAnalysisClient;
    private final GeminiService geminiService;

    public AIAnalysisResponse analyzeUserJudgment(Long userId, Long newsId, AIAnalysisRequest aiAnalysisRequest) {
        NewsArticle newsArticle = newsArticleRepository.findWithRelatedStocksByNewsId(newsId)
                .orElseThrow(() -> new CustomException(ErrorCode.NEWS_NOT_FOUND));

        ExternalAIResponse externalResponse = aiAnalysisClient.analyze(toExternalRequest(newsArticle));

        ExternalAIResponse.SummaryInfo summary = externalResponse.getSummary();
        NewsSentiment aiSentiment = mapToNewsSentiment(summary.getOverall_sentiment());
        boolean isCorrect = aiAnalysisRequest.getSentiment() == aiSentiment;

        String feedback = geminiService.generateContent(buildPrompt(newsArticle, summary, aiAnalysisRequest, isCorrect));

        newsService.markNewsAsLearned(userId, newsId);

        return AIAnalysisResponse.builder()
                .aiSentiment(aiSentiment)
                .aiFeedback(feedback)
                .isCorrect(isCorrect)
                .build();
    }

    private ExternalAIRequest toExternalRequest(NewsArticle newsArticle) {
        String content = newsArticle.getContent();
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        return ExternalAIRequest.builder()
                .request_id(UUID.randomUUID().toString())
                .article(ExternalAIRequest.ArticleInfo.builder()
                        .article_id(newsArticle.getNewsId())
                        .title(newsArticle.getTitle())
                        .content(content)
                        .articleDate(newsArticle.getArticleDate().toString())
                        .source(newsArticle.getSource())
                        .relatedStocks(newsArticle.getRelatedStocks().stream()
                                .map(Stock::getStockCode)
                                .toList())
                        .build())
                .options(ExternalAIRequest.AnalysisOptions.builder()
                        .max_snippets(12)
                        .include_weak_snippets(false)
                        .include_raw_model_output(false)
                        .build())
                .build();
    }

    private String buildPrompt(NewsArticle newsArticle, ExternalAIResponse.SummaryInfo summary,
            AIAnalysisRequest request, boolean isCorrect) {
        return String.format(
                "당신은 학생들의 투자 판단을 평가하고 가르치는 전문 금융 AI 튜터입니다.\n\n" +
                "이하의 뉴스 기사와 이에 대한 인공지능 분석 결과, 그리고 학생의 판단 내용 및 이유를 바탕으로 한국어로 친근하고 전문적인 피드백을 작성해 주세요.\n\n" +
                "1. 뉴스 기사\n" +
                "- 제목: %s\n" +
                "- 본문: %s\n\n" +
                "2. 인공지능 감정 분석 결과 (참고 정보)\n" +
                "- 전체 의견 (Overall Sentiment): %s\n" +
                "- 호재 점수: %.2f, 악재 점수: %.2f, 중립 점수: %.2f\n" +
                "- 호재 키워드: %s\n" +
                "- 악재 키워드: %s\n" +
                "- 주된 카테고리: %s\n\n" +
                "3. 학생의 투자 판단\n" +
                "- 선택한 감정 (Sentiment): %s\n" +
                "- 그렇게 분석한 이유: %s\n\n" +
                "학생의 답변이 인공지능 분석과 비교했을 때 %s 판단인지 평가해 주시고, 왜 그렇게 분석하는 것이 옳은지(혹은 틀렸는지)를 뉴스 본문의 핵심 맥락과 AI 분석 결과를 인용하며 학습 관점에서 친절하게 설명해 주세요. 마지막으로 이 뉴스에서 기억해야 할 금융 지식이나 교훈을 1~2문장으로 덧붙여 주세요.",
                newsArticle.getTitle(),
                newsArticle.getContent(),
                summary.getOverall_sentiment(),
                summary.getPositive_score(), summary.getNegative_score(), summary.getNeutral_score(),
                summary.getPositive_keywords() != null ? String.join(", ", summary.getPositive_keywords()) : "없음",
                summary.getNegative_keywords() != null ? String.join(", ", summary.getNegative_keywords()) : "없음",
                summary.getDominant_categories() != null ? String.join(", ", summary.getDominant_categories()) : "없음",
                request.getSentiment() == NewsSentiment.POSITIVE ? "호재 (POSITIVE)"
                        : (request.getSentiment() == NewsSentiment.NEGATIVE ? "악재 (NEGATIVE)" : "중립 (NEUTRAL)"),
                request.getReason(),
                isCorrect ? "올바른" : "틀린");
    }

    private NewsSentiment mapToNewsSentiment(String sentimentStr) {
        if (sentimentStr == null) {
            return NewsSentiment.NEUTRAL;
        }
        return switch (sentimentStr.toLowerCase()) {
            case "positive" -> NewsSentiment.POSITIVE;
            case "negative" -> NewsSentiment.NEGATIVE;
            default -> NewsSentiment.NEUTRAL;
        };
    }
}
