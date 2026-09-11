package org.gp.newspinbe.domain.news.presentation;

import org.gp.newspinbe.domain.news.application.AIService;
import org.gp.newspinbe.domain.news.application.NewsService;
import org.gp.newspinbe.domain.news.dto.request.AIAnalysisRequest;
import org.gp.newspinbe.domain.news.dto.response.AIAnalysisResponse;
import org.gp.newspinbe.domain.news.dto.response.NewsResponse;
import org.gp.newspinbe.global.common.ApiResponse;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "뉴스 학습", description = "뉴스 감정 판단 학습 — 랜덤 뉴스 제시 후 사용자 판단을 AI 채점과 대조")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final AIService aiService;

    @Operation(summary = "학습용 랜덤 뉴스 조회", description = "아직 판단하지 않은 뉴스 1건을 무작위로 반환한다.")
    @GetMapping("/random")
    public ResponseEntity<ApiResponse<NewsResponse>> getRandomNews(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        NewsResponse response = newsService.getRandomUnlearnedNews(userDetails.getUser().getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "뉴스 감정 판단 제출", description = "사용자의 긍/부정 판단을 AI(newspin-ai) 채점 결과와 비교해 정오를 매기고, "
            + "Gemini 로 생성한 튜터 피드백과 함께 반환한다. 판단 정오는 리포트(I-11)의 학습 정확도 섹션에도 반영된다(S-2).")
    @PostMapping("/{newsId}/analyze")
    public ResponseEntity<ApiResponse<AIAnalysisResponse>> analyzeNews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long newsId,
            @RequestBody @Valid AIAnalysisRequest aiAnalysisRequest) {
        AIAnalysisResponse response = aiService.analyzeUserJudgment(
                userDetails.getUser().getUserId(), newsId, aiAnalysisRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
