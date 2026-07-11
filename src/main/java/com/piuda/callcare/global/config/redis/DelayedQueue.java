package com.piuda.callcare.global.config.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 지연 큐 프리미티브 (Redis Sorted Set 기반).
 * <p>
 * {@code score = 실행 예정 시각(epoch millis)}으로 payload를 ZSET에 넣고,
 * {@code score <= now}인 항목을 꺼낸다.
 * <p>
 * {@link #pollDue}는 조회(ZRANGEBYSCORE)와 삭제(ZREM)를 <b>하나의 Lua 스크립트</b>로 실행해
 * 원자적으로 pop 한다. 동시 폴링에서도 같은 항목이 두 번 나오지 않는다.
 * (ZRANGEBYSCORE 후 별도 ZREM 하는 비원자 방식은 레이스가 생기므로 쓰지 않는다.)
 * <p>
 * 키 네이밍 규칙: {@code delayqueue:{큐 이름}} — prefix는 이 큐가 붙인다.
 */
@Component
@RequiredArgsConstructor
public class DelayedQueue {

	private static final String KEY_PREFIX = "delayqueue:";
	private static final int DEFAULT_POLL_LIMIT = 100;

	/**
	 * score <= now 인 항목을 limit 개까지 꺼내고(ZREM) 그 payload 목록을 반환한다.
	 * KEYS[1]=큐 키, ARGV[1]=now(millis), ARGV[2]=limit.
	 */
	private static final RedisScript<List> POLL_DUE_SCRIPT = RedisScript.of(
		"local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2]) "
			+ "if #due > 0 then redis.call('ZREM', KEYS[1], unpack(due)) end "
			+ "return due",
		List.class);

	private final StringRedisTemplate stringRedisTemplate;

	/**
	 * payload를 {@code now + delay} 시각에 실행되도록 큐에 넣는다.
	 *
	 * @param queueKey 큐 이름 (prefix 제외)
	 * @param payload  실행할 작업의 JSON 문자열
	 * @param delay    지금부터의 지연 시간
	 */
	public void enqueue(String queueKey, String payload, Duration delay) {
		long score = Instant.now().toEpochMilli() + delay.toMillis();
		stringRedisTemplate.opsForZSet().add(prefixed(queueKey), payload, score);
	}

	// 실행 예정 시각이 지난 항목을 기본 개수(100)까지 원자적으로 꺼낸다
	public List<String> pollDue(String queueKey) {
		return pollDue(queueKey, DEFAULT_POLL_LIMIT);
	}

	/**
	 * 실행 예정 시각({@code score})이 현재 시각 이하인 항목을 limit 개까지 원자적으로 꺼낸다.
	 * 꺼낸 항목은 큐에서 제거되므로 다시 pollDue 해도 중복으로 나오지 않는다.
	 */
	@SuppressWarnings("unchecked")
	public List<String> pollDue(String queueKey, int limit) {
		long now = Instant.now().toEpochMilli();
		List<String> due = stringRedisTemplate.execute(
			POLL_DUE_SCRIPT,
			List.of(prefixed(queueKey)),
			String.valueOf(now),
			String.valueOf(limit));
		return due != null ? due : List.of();
	}

	private String prefixed(String key) {
		return KEY_PREFIX + key;
	}
}