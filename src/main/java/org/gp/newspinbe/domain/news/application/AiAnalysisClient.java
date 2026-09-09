package org.gp.newspinbe.domain.news.application;

import java.util.function.Supplier;

import org.gp.newspinbe.domain.news.dto.request.ExternalAIRequest;
import org.gp.newspinbe.domain.news.dto.response.ExternalAIResponse;
import org.gp.newspinbe.global.config.ResilienceConfig;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * newspin-ai 감정 분석 호출. 재시도 + 서킷브레이커로 감싸, AI 서버 장애가
 * 사용자 요청 실패로 그대로 전파되지 않도록 한다 (S2, R-1·R-2).
 * 트랜잭션을 열지 않는다 — 호출자가 트랜잭션 밖에서 부른다.
 */
@Slf4j
@Component
public class AiAnalysisClient {

    private final RestClient aiAnalysisRestClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public AiAnalysisClient(RestClient aiAnalysisRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.aiAnalysisRestClient = aiAnalysisRestClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(ResilienceConfig.AI_ANALYSIS);
        this.retry = retryRegistry.retry(ResilienceConfig.AI_ANALYSIS);
    }

    public ExternalAIResponse analyze(ExternalAIRequest request) {
        Supplier<ExternalAIResponse> supplier = Retry.decorateSupplier(retry, () -> call(request));
        supplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        try {
            ExternalAIResponse response = supplier.get();
            if (response == null || response.getSummary() == null) {
                throw new CustomException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return response;
        } catch (CallNotPermittedException e) {
            log.warn("AI 서비스 서킷 오픈 — 빠른 실패");
            throw new CustomException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 감정 분석 호출 실패: {}", e.toString());
            throw new CustomException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private ExternalAIResponse call(ExternalAIRequest request) {
        return aiAnalysisRestClient.post()
                .uri("/api/v1/analyze")
                .body(request)
                .retrieve()
                .body(ExternalAIResponse.class);
    }
}
