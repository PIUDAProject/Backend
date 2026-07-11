package com.piuda.callcare.global.config.redis;

/**
 * 지연 큐에서 꺼낸 payload를 처리하는 핸들러.
 * <p>
 * 이번 작업은 인프라 토대만 만들며, 실제 처리 로직(FCM 푸시, 전화 재시도 등)은
 * 후속 작업에서 이 인터페이스 구현체를 끼워 넣는다.
 * 기본 구현은 로그만 남기는 no-op 스텁({@link LoggingDelayedJobHandler})이다.
 */
public interface DelayedJobHandler {

	// 지연 큐에서 due 상태로 꺼낸 payload 1건을 처리한다
	void handle(String payload);
}