package com.piuda.callcare.global.config.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("DelayedQueue 통합 테스트")
class DelayedQueueIT extends AbstractRedisIntegrationTest {

	private static final String QUEUE = "notification";

	@Autowired
	private DelayedQueue delayedQueue;

	@Test
	@DisplayName("정상 케이스: delay 이전에는 비어 있고 delay 이후에는 payload가 나온다")
	void pollDue_returnsPayload_onlyAfterDelay() throws InterruptedException {
		// Given
		String payload = "{\"type\":\"CALL\",\"seniorId\":42}";
		delayedQueue.enqueue(QUEUE, payload, Duration.ofMillis(500));

		// When: delay 이전
		List<String> beforeDelay = delayedQueue.pollDue(QUEUE);

		// Then: 아직 나오지 않는다
		assertThat(beforeDelay).isEmpty();

		// When: delay 이후
		Thread.sleep(700);
		List<String> afterDelay = delayedQueue.pollDue(QUEUE);

		// Then: 나온다
		assertThat(afterDelay).containsExactly(payload);
	}

	@Test
	@DisplayName("원자성: due 항목을 pollDue로 꺼낸 뒤 다시 pollDue 하면 중복으로 나오지 않는다")
	void pollDue_removesItem_soSecondPollIsEmpty() {
		// Given: 즉시 due 상태로 넣는다
		String payload = "{\"type\":\"PUSH\",\"seniorId\":7}";
		delayedQueue.enqueue(QUEUE, payload, Duration.ZERO);

		// When
		List<String> firstPoll = delayedQueue.pollDue(QUEUE);
		List<String> secondPoll = delayedQueue.pollDue(QUEUE);

		// Then
		assertThat(firstPoll).containsExactly(payload);
		assertThat(secondPoll).isEmpty();
	}

	@Test
	@DisplayName("정상 케이스: 여러 due 항목을 한 번에 모두 꺼낸다")
	void pollDue_returnsAllDueItems() {
		// Given
		delayedQueue.enqueue(QUEUE, "job-1", Duration.ZERO);
		delayedQueue.enqueue(QUEUE, "job-2", Duration.ZERO);
		delayedQueue.enqueue(QUEUE, "job-future", Duration.ofMinutes(10));

		// When
		List<String> due = delayedQueue.pollDue(QUEUE);

		// Then: due 2건만 나오고, 미래 항목은 남는다
		assertThat(due).containsExactlyInAnyOrder("job-1", "job-2");
		assertThat(delayedQueue.pollDue(QUEUE)).isEmpty();
	}
}