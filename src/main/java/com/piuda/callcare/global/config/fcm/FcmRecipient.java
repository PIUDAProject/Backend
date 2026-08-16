package com.piuda.callcare.global.config.fcm;

/**
 * FCM 푸시 수신 대상 1명.
 * <p>
 * {@code userId}로 활성 토큰을 조회하고, {@code seniorId}는 발송 이력(Notification)에 필요하다
 * (Notification의 senior_id가 NOT NULL — 이 앱의 푸시는 항상 "보호자에게 특정 어르신에 대해" 보낸다).
 */
public record FcmRecipient(Long userId, Long seniorId) {
}
