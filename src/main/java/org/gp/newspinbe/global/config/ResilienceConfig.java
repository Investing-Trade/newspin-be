package org.gp.newspinbe.global.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClientResponseException;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 외부 AI 연동(newspin-ai)의 회복탄력성 설정 (S2, R-2).
 * 프로그래밍 방식으로 데코레이트하므로 resilience4j-spring-boot 스타터는 쓰지 않는다.
 */
@Configuration
public class ResilienceConfig {

    public static final String AI_ANALYSIS = "aiAnalysis";

    @Bean
    public RetryRegistry retryRegistry(MeterRegistry meterRegistry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2) // 1회 재시도. 그 이상은 서킷브레이커가 빠른 실패로 처리
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(Duration.ofMillis(500), 2.0))
                .retryOnException(ResilienceConfig::isTransient)
                .build();
        RetryRegistry registry = RetryRegistry.of(config);
        TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordException(ResilienceConfig::isTransient)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    /** 재시도/서킷 카운트 대상 — 네트워크 오류, 429, 5xx. 4xx(요청 문제)는 제외. */
    private static boolean isTransient(Throwable t) {
        if (t instanceof RestClientResponseException re) {
            int code = re.getStatusCode().value();
            return code == 429 || code >= 500;
        }
        // ResourceAccessException(연결 실패/타임아웃) 등 나머지 RestClientException 계열
        return t instanceof org.springframework.web.client.RestClientException;
    }
}
