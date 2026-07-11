package com.piuda.callcare.global.config.redis;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link DelayedJobHandler}의 기본 no-op 스텁 구현.
 * <p>
 * 꺼낸 payload를 실제 도메인 동작에 연결하지 않고 로그만 남긴다.
 * 실제 처리 핸들러가 추가되면 이 스텁을 교체한다.
 */
@Slf4j
@Component
public class LoggingDelayedJobHandler implements DelayedJobHandler {

	@Override
	public void handle(String payload) {
		log.info("[DelayedQueue] due payload 수신 (아직 처리 핸들러 없음, no-op): {}", payload);
	}
}