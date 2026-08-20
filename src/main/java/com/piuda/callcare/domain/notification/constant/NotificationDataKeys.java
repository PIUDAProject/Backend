package com.piuda.callcare.domain.notification.constant;

/**
 * 푸시 payload의 딥링크 키 — 알림을 탭했을 때 어느 화면으로 갈지 특정하는 값들의 이름.
 * <p>
 * <b>키 이름은 프론트 합의 전 잠정값이다.</b> 합의되면 이 파일의 상수 값만 바꾸면 되도록
 * 문자열을 코드에 흩뿌리지 않고 여기에 모은다.
 * <p>
 * {@code type}과 {@code notificationId}는 여기에 없다 — 모든 알림에 공통이라
 * 발송 계층({@code FcmSendService})이 직접 얹는다.
 */
public final class NotificationDataKeys {

    // 어느 어르신에 대한 알림인지 (두 알림 공통)
    public static final String SENIOR_ID = "seniorId";

    // 소진 알림 → 해당 약 리포트로 이동
    public static final String MEDICATION_ID = "medicationId";

    // 충돌 알림 → 해당 충돌 리포트로 이동
    public static final String DRUG_CONFLICT_ID = "drugConflictId";

    private NotificationDataKeys() {
    }
}
