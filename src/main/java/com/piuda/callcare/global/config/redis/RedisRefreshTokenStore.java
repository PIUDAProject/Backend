package com.piuda.callcare.global.config.redis;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore {

	private static final String KEY_PREFIX = "RT:";

	private final StringRedisTemplate stringRedisTemplate;

	/**
	 * 리프레시 토큰 저장 (RTR 전략: 기존 키가 있으면 덮어쓰기)
	 * @param userId 유저 식별자
	 * @param refreshToken 발급된 리프레시 토큰
	 * @param expiryTimeMillis 토큰의 만료 시간 (ms)
	 */
	public void saveRefreshToken(String userId, String refreshToken, long expiryTimeMillis) {
		stringRedisTemplate.opsForValue().set(
			KEY_PREFIX + userId,
			refreshToken,
			expiryTimeMillis,
			TimeUnit.MILLISECONDS
		);
	}

	// 저장된 리프레시 토큰 조회
	public String getRefreshToken(String userId) {
		return stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
	}

	// 리프레시 토큰 삭제
	public void removeRefreshToken(String userId) {
		stringRedisTemplate.delete(KEY_PREFIX + userId);
	}
}
