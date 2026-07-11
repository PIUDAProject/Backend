package com.piuda.callcare.global.config.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 멱등성 키 프리미티브.
 * <p>
 * "이 키를 지금 처음 잡는가?"를 원자적으로 판정해 같은 알림의 중복 실행을 막는다.
 * 반드시 {@code SETNX + TTL}({@code setIfAbsent(value, ttl)}) 원자 연산만 사용하며,
 * "조회 후 저장" 같은 비원자 패턴은 쓰지 않는다.
 * <p>
 * 키 네이밍 규칙: {@code idem:{도메인}:{용도 식별자}} — prefix는 이 스토어가 붙인다.
 * 호출부는 도메인/용도가 구분되는 논리 키(예: {@code "call:senior:42:2026-07-11:BREAKFAST"})만 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyStore {

	private static final String KEY_PREFIX = "idem:";
	private static final String ACQUIRED_MARKER = "1";

	private final StringRedisTemplate stringRedisTemplate;

	/**
	 * 키를 처음 잡으면 TTL과 함께 마킹하고 true, 이미 잡혀 있으면 false를 반환한다(원자적).
	 *
	 * @param key 도메인/용도가 구분되는 논리 키 (prefix 제외)
	 * @param ttl 멱등성 유지 기간
	 * @return 이번 호출이 최초 획득이면 true
	 */
	public boolean tryAcquire(String key, Duration ttl) {
		Boolean acquired = stringRedisTemplate.opsForValue()
			.setIfAbsent(prefixed(key), ACQUIRED_MARKER, ttl);
		return Boolean.TRUE.equals(acquired);
	}

	// 이미 처리된(키가 잡혀 있는) 상태인지 확인
	public boolean isProcessed(String key) {
		return Boolean.TRUE.equals(stringRedisTemplate.hasKey(prefixed(key)));
	}

	// 멱등성 키 해제 (재실행을 허용해야 하는 예외 상황용)
	public void release(String key) {
		stringRedisTemplate.delete(prefixed(key));
	}

	private String prefixed(String key) {
		return KEY_PREFIX + key;
	}
}