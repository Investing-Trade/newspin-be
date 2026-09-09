package org.gp.newspinbe.global.util;

import java.time.Duration;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisUtil {

	private final StringRedisTemplate redisTemplate;

	public String getData(String key) {
		ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
		return valueOperations.get(key);
	}

	public boolean existData(String key) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key));
	}

	public void setDataExpire(String key, String value, long duration) {
		ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
		Duration expireDuration = Duration.ofSeconds(duration);
		valueOperations.set(key, value, expireDuration);
	}

	public void deleteData(String key) {
		redisTemplate.delete(key);
	}

	/** 패턴에 해당하는 키를 모두 삭제 (예: 한 유저의 모든 리프레시 토큰). */
	public void deleteByPattern(String pattern) {
		Set<String> keys = redisTemplate.keys(pattern);
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}
}
