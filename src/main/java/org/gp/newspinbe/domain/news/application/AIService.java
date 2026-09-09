package org.gp.newspinbe.domain.news.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AIService {

    private final NewsArticleRepository newsArticleRepository;
    private final NewsService newsService;
    private final RestClient aiAnalysisRestClient;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai-service.url}")
    private String aiServiceUrl;

    @Transactional
    public AIAnalysisResponse analyzeUserJudgment(Long userId, Long newsId, AIAnalysisRequest aiAnalysisRequest) {

        NewsArticle newsArticle = newsArticleRepository.findById(newsId)
                .orElseThrow(() -> new CustomException(ErrorCode.NEWS_NOT_FOUND));

        String content = newsArticle.getContent();
        if (content != null && content.length() > 2000) {
            content = content.substring(0, 2000);
        }

        ExternalAIRequest requestPayload = ExternalAIRequest.builder()
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

        ExternalAIResponse externalResponse;
        try {
            String requestJson = objectMapper.writeValueAsString(requestPayload);
            log.info("aiServiceUrl: {}", aiServiceUrl);
            log.info("requestJson length: {}", requestJson.length());
            log.info("AI 서버로 전송할 requestJson: {}", requestJson);

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceUrl + "/api/v1/analyze"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

            log.info("AI 서버 응답 상태코드: {}", httpResponse.statusCode());
            log.info("AI 서버 응답 body: {}", httpResponse.body());

            externalResponse = objectMapper.readValue(httpResponse.body(), ExternalAIResponse.class);
        } catch (Exception e) {
            log.error("외부 감정 분석 AI API 호출 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        if (externalResponse == null || externalResponse.getSummary() == null) {
            log.error("외부 감정 분석 AI 응답 또는 요약 정보가 null입니다.");
            throw new CustomException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        NewsSentiment aiSentiment = mapToNewsSentiment(externalResponse.getSummary().getOverall_sentiment());
        boolean isCorrect = aiAnalysisRequest.getSentiment() == aiSentiment;

        String prompt = String.format(
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
                externalResponse.getSummary().getOverall_sentiment(),
                externalResponse.getSummary().getPositive_score(),
                externalResponse.getSummary().getNegative_score(),
                externalResponse.getSummary().getNeutral_score(),
                externalResponse.getSummary().getPositive_keywords() != null ? String.join(", ", externalResponse.getSummary().getPositive_keywords()) : "없음",
                externalResponse.getSummary().getNegative_keywords() != null ? String.join(", ", externalResponse.getSummary().getNegative_keywords()) : "없음",
                externalResponse.getSummary().getDominant_categories() != null ? String.join(", ", externalResponse.getSummary().getDominant_categories()) : "없음",
                aiAnalysisRequest.getSentiment() == NewsSentiment.POSITIVE ? "호재 (POSITIVE)" : (aiAnalysisRequest.getSentiment() == NewsSentiment.NEGATIVE ? "악재 (NEGATIVE)" : "중립 (NEUTRAL)"),
                aiAnalysisRequest.getReason(),
                isCorrect ? "올바른" : "틀린"
        );

        String feedback = geminiService.generateContent(prompt);

        newsService.markNewsAsLearned(userId, newsId);

        return AIAnalysisResponse.builder()
                .aiSentiment(aiSentiment)
                .aiFeedback(feedback)
                .isCorrect(isCorrect)
                .build();
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