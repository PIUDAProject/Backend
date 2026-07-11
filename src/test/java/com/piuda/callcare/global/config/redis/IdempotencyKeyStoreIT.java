package com.piuda.callcare.global.config.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("IdempotencyKeyStore 통합 테스트")
class IdempotencyKeyStoreIT extends AbstractRedisIntegrationTest {

	@Autowired
	private IdempotencyKeyStore idempotencyKeyStore;

	@Test
	@DisplayName("정상 케이스: 같은 키를 처음 잡으면 true, 이미 잡혀 있으면 false를 반환한다")
	void tryAcquire_returnsFalse_whenKeyAlreadyHeld() {
		// Given
		String key = "call:senior:42:2026-07-11:BREAKFAST";

		// When
		boolean first = idempotencyKeyStore.tryAcquire(key, Duration.ofMinutes(10));
		boolean second = idempotencyKeyStore.tryAcquire(key, Duration.ofMinutes(10));

		// Then
		assertThat(first).isTrue();
		assertThat(second).isFalse();
	}

	@Test
	@DisplayName("정상 케이스: TTL이 만료되면 같은 키를 다시 잡을 수 있다")
	void tryAcquire_returnsTrueAgain_afterTtlExpires() throws InterruptedException {
		// Given
		String key = "call:senior:42:2026-07-11:LUNCH";
		idempotencyKeyStore.tryAcquire(key, Duration.ofMillis(500));

		// When: TTL 만료까지 대기
		Thread.sleep(700);
		boolean afterExpiry = idempotencyKeyStore.tryAcquire(key, Duration.ofMinutes(10));

		// Then
		assertThat(afterExpiry).isTrue();
	}

	@Test
	@DisplayName("보조 메서드: isProcessed는 키 보유 여부를, release는 해제를 반영한다")
	void isProcessed_and_release() {
		// Given
		String key = "call:senior:42:2026-07-11:DINNER";

		// When / Then
		assertThat(idempotencyKeyStore.isProcessed(key)).isFalse();

		idempotencyKeyStore.tryAcquire(key, Duration.ofMinutes(10));
		assertThat(idempotencyKeyStore.isProcessed(key)).isTrue();

		idempotencyKeyStore.release(key);
		assertThat(idempotencyKeyStore.isProcessed(key)).isFalse();
		assertThat(idempotencyKeyStore.tryAcquire(key, Duration.ofMinutes(10))).isTrue();
	}
}