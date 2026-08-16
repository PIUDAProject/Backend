package com.piuda.callcare.global.config.fcm;

/**
 * FCM 발송 결과 집계.
 *
 * @param status                발송 시도 결과 — 호출자가 재시도·폴백을 판단하는 기준
 * @param successCount          토큰 단위 성공 건수
 * @param failureCount          토큰 단위 실패 건수
 * @param deactivatedTokenCount 무효 토큰으로 판별되어 비활성화된 건수
 */
public record FcmSendResult(
    FcmSendStatus status,
    int successCount,
    int failureCount,
    int deactivatedTokenCount
) {

    // FCM 미설정으로 발송을 건너뛴 경우
    public static FcmSendResult notConfigured() {
        return new FcmSendResult(FcmSendStatus.NOT_CONFIGURED, 0, 0, 0);
    }

    // 활성 토큰이 없어 보낼 대상 자체가 없던 경우
    public static FcmSendResult noToken() {
        return new FcmSendResult(FcmSendStatus.NO_TOKEN, 0, 0, 0);
    }

    // 발송을 시도한 경우 — 한 건이라도 성공하면 SENT, 전 건 실패면 SEND_FAILED
    public static FcmSendResult completed(int successCount, int failureCount, int deactivatedTokenCount) {
        FcmSendStatus status = successCount > 0 ? FcmSendStatus.SENT : FcmSendStatus.SEND_FAILED;
        return new FcmSendResult(status, successCount, failureCount, deactivatedTokenCount);
    }
}