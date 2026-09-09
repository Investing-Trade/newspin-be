package org.gp.newspinbe.global.config;

import java.time.Duration;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Redis 캐시 (I-3). 대상은 <b>과거 확정 데이터</b>(시세 히스토리) 라서 무효화가 필요 없고
 * TTL 만으로 충분하다. Redis 장애 시 캐시를 우회하고 원본 조회로 진행한다(예외 전파 X).
 * 값 직렬화는 기본 JDK 직렬화 — 캐시 대상 DTO 는 {@code Serializable} 이다.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String PRICE_HISTORY = "priceHistory";
    public static final String PRICE_HISTORY_ALL = "priceHistoryAll";

    @org.springframework.context.annotation.Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer() {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(Duration.ofHours(6));
        return builder -> builder
                .withCacheConfiguration(PRICE_HISTORY, base)
                .withCacheConfiguration(PRICE_HISTORY_ALL, base);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(); // get/put/evict 실패를 로그만 남기고 캐시 우회
    }
}
