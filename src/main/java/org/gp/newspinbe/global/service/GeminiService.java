package org.gp.newspinbe.global.service;

import java.util.List;
import java.util.Map;

import org.gp.newspinbe.global.config.RestClientConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final RestClient geminiRestClient;
    private final RestClientConfig restClientConfig;

    public String generateContent(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 4096));

        try {
            Map<?, ?> response = geminiRestClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path(":generateContent")
                            .queryParam("key", restClientConfig.getGeminiApiKey())
                            .build())
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.error("Gemini API 응답이 null입니다.");
                return "AI 분석을 수행할 수 없습니다.";
            }

            // 응답 파싱: response.candidates[0].content.parts[0].text
            List<?> candidates = (List<?>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.error("Gemini API 응답에 candidates가 없습니다.");
                return "AI 분석을 수행할 수 없습니다.";
            }

            Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) candidate.get("content");
            List<?> parts = (List<?>) content.get("parts");
            Map<?, ?> part = (Map<?, ?>) parts.get(0);

            return (String) part.get("text");

        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage(), e);
            return "AI 분석 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
