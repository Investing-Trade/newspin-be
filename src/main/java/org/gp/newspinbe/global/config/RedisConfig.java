package org.gp.newspinbe.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * {@code RedisConnectionFactory} 는 Spring Boot 오토컨피그({@code spring.data.redis.*}) 에 맡긴다 (I-18).
 * 이전엔 여기서 {@code @Value} 로 팩토리를 직접 만들어 오토컨피그·{@code @ServiceConnection}·health 를
 * 우회하고, 기본값이 없어 환경변수 미설정 시 컨텍스트 로드가 실패했다.
 */
@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new StringRedisSerializer());
		return template;
	}
}
