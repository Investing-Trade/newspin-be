package org.gp.newspinbe.global.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.gp.newspinbe.global.config.RestClientConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gemini generateContent 호출. 외부 LLM 은 성공을 보장하지 않으므로
 * (a) 일시 오류(네트워크/5xx/429)는 짧게 재시도하고
 * (b) 안전 필터 차단·응답 형식 이상은 재시도 없이 사용자에게 보여줄 fallback 을 돌려준다. (C-4)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private static final int MAX_ATTEMPTS = 4;
    private static final long[] BACKOFF_MS = {0L, 600L, 1500L, 3000L};

    /** text 없이 생성이 끝난 경우의 사유들 — 재시도해도 동일하므로 즉시 fallback. */
    private static final Set<String> BLOCKED_REASONS =
            Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "OTHER");

    static final String FALLBACK_BLOCKED = "AI가 이 내용에 대한 답변을 생성하지 못했습니다. 다른 표현으로 다시 시도해 주세요.";
    static final String FALLBACK_ERROR = "AI 분석을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.";

    private final RestClient geminiRestClient;
    private final RestClientConfig restClientConfig;

    private static final Map<String, Object> FREE_TEXT_CONFIG =
            Map.of("temperature", 0.7, "maxOutputTokens", 4096);

    /** 자유 텍스트 생성 (튜터 피드백 등). */
    public String generateContent(String prompt) {
        return generate(prompt, FREE_TEXT_CONFIG);
    }

    /**
     * JSON 응답 강제 (I-4). {@code responseMimeType=application/json} + 스키마.
     * 반환값이 유효한 JSON 이 아니면(fallback 메시지 등) 호출자가 처리한다.
     */
    public String generateJson(String prompt, Map<String, Object> responseSchema) {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("temperature", 0.2);
        config.put("maxOutputTokens", 8192);
        config.put("responseMimeType", "application/json");
        if (responseSchema != null) {
            config.put("responseSchema", responseSchema);
        }
        return generate(prompt, config);
    }

    private String generate(String prompt, Map<String, Object> generationConfig) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Map<?, ?> response = call(prompt, generationConfig);
                return parseText(response); // 파싱 실패/차단은 GeminiUnavailableException 로 던짐 (재시도 안 함)
            } catch (GeminiBlockedException e) {
                log.warn("Gemini 응답 차단 - finishReason: {}", e.getMessage());
                return FALLBACK_BLOCKED;
            } catch (GeminiUnavailableException e) {
                log.error("Gemini 응답 형식 이상: {}", e.getMessage());
                return FALLBACK_ERROR;
            } catch (ResourceAccessException | RestClientResponseException e) {
                last = e;
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    break;
                }
                log.warn("Gemini 호출 실패 (attempt {}/{}), 재시도: {}", attempt, MAX_ATTEMPTS, e.toString());
                sleep(BACKOFF_MS[attempt]);
            } catch (Exception e) {
                log.error("Gemini 호출 중 예상치 못한 오류", e);
                return FALLBACK_ERROR;
            }
        }
        log.error("Gemini 호출 재시도 소진", last);
        return FALLBACK_ERROR;
    }

    private final ObjectMapper json = new ObjectMapper();

    private Map<?, ?> call(String prompt, Map<String, Object> generationConfig) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig);

        // exchange 로 원본 바이트를 직접 읽어 파싱 — Gemini 가 가끔 application/octet-stream 으로
        // 응답해 RestClient 메시지 컨버터가 String/Map 변환을 거부하는 경우 회피.
        return geminiRestClient.post()
                .uri(b -> b.path(":generateContent")
                        .queryParam("key", restClientConfig.getGeminiApiKey())
                        .build())
                .body(body)
                .exchange((request, response) -> {
                    byte[] bytes = response.getBody().readAllBytes();
                    if (response.getStatusCode().isError()) {
                        throw new RestClientResponseException(
                                "Gemini " + response.getStatusCode().value(),
                                response.getStatusCode(),
                                response.getStatusText(),
                                response.getHeaders(),
                                bytes,
                                java.nio.charset.StandardCharsets.UTF_8);
                    }
                    try {
                        return json.readValue(bytes, Map.class);
                    } catch (Exception e) {
                        throw new GeminiUnavailableException("응답 JSON 파싱 실패: " + e.getMessage());
                    }
                });
    }

    static String parseText(Map<?, ?> response) {
        if (response == null) {
            throw new GeminiUnavailableException("response is null");
        }
        // 프롬프트 자체가 차단된 경우
        Map<?, ?> promptFeedback = asMap(response.get("promptFeedback"));
        if (promptFeedback != null && promptFeedback.get("blockReason") != null) {
            throw new GeminiBlockedException("prompt:" + promptFeedback.get("blockReason"));
        }

        List<?> candidates = asList(response.get("candidates"));
        if (candidates == null || candidates.isEmpty()) {
            throw new GeminiUnavailableException("no candidates");
        }
        Map<?, ?> candidate = asMap(candidates.get(0));
        String finishReason = candidate == null ? null : String.valueOf(candidate.get("finishReason"));

        String text = extractText(candidate);
        if (text != null && !text.isBlank()) {
            return text;
        }
        if (finishReason != null && BLOCKED_REASONS.contains(finishReason)) {
            throw new GeminiBlockedException(finishReason);
        }
        throw new GeminiUnavailableException("empty text (finishReason=" + finishReason + ")");
    }

    private static String extractText(Map<?, ?> candidate) {
        if (candidate == null) {
            return null;
        }
        Map<?, ?> content = asMap(candidate.get("content"));
        if (content == null) {
            return null;
        }
        List<?> parts = asList(content.get("parts"));
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            Map<?, ?> part = asMap(p);
            Object t = part == null ? null : part.get("text");
            if (t != null) {
                sb.append(t);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private boolean isRetryable(Exception e) {
        if (e instanceof ResourceAccessException) {
            return true; // 연결 실패 / 타임아웃
        }
        if (e instanceof RestClientResponseException re) {
            int code = re.getStatusCode().value();
            return code == 429 || code >= 500;
        }
        return false;
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map<?, ?> m ? m : null;
    }

    private static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : null;
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    static class GeminiBlockedException extends RuntimeException {
        GeminiBlockedException(String reason) {
            super(reason);
        }
    }

    static class GeminiUnavailableException extends RuntimeException {
        GeminiUnavailableException(String reason) {
            super(reason);
        }
    }
}
