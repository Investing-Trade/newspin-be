package org.gp.newspinbe.domain.news.presentation;

import org.gp.newspinbe.domain.news.application.NewsService;
import org.gp.newspinbe.domain.news.dto.response.NewsResponse;
import org.gp.newspinbe.global.common.ApiResponse;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping("/random")
    public ResponseEntity<ApiResponse<NewsResponse>> getRandomNews(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        NewsResponse response = newsService.getRandomUnlearnedNews(userDetails.getUser().getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
