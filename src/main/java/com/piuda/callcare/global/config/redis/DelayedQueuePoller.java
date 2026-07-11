package com.piuda.callcare.global.config.redis;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지연 큐를 주기적으로 폴링해 due 항목을 {@link DelayedJobHandler}로 넘기는 스케줄러.
 * <p>
 * 폴링 주기는 {@code notification.delayed-queue.poll-interval-ms}(기본 1000ms)로 조정한다.
 * {@code notification.delayed-queue.poller-enabled=false}로 폴링을 끌 수 있다(테스트/부분 배포용).
 * <p>
 * 지금은 핸들러가 no-op 스텁이라 실제 도메인 동작으로 이어지지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.delayed-queue.poller-enabled", havingValue = "true", matchIfMissing = true)
public class DelayedQueuePoller {

	// 알림 지연 작업이 쌓이는 기본 큐 이름 (prefix 제외)
	public static final String NOTIFICATION_QUEUE_KEY = "notification";

	private final DelayedQueue delayedQueue;
	private final DelayedJobHandler delayedJobHandler;

	@Scheduled(fixedDelayString = "${notification.delayed-queue.poll-interval-ms:1000}")
	public void poll() {
		List<String> duePayloads = delayedQueue.pollDue(NOTIFICATION_QUEUE_KEY);
		for (String payload : duePayloads) {
			try {
				delayedJobHandler.handle(payload);
			} catch (Exception e) {
				log.error("[DelayedQueue] payload 처리 중 오류 (payload={}): {}", payload, e.getMessage(), e);
			}
		}
	}
}