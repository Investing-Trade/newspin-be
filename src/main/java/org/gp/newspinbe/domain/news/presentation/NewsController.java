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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final AIService aiService;

    @GetMapping("/random")
    public ResponseEntity<ApiResponse<NewsResponse>> getRandomNews(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        NewsResponse response = newsService.getRandomUnlearnedNews(userDetails.getUser().getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

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
