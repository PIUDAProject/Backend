package com.piuda.callcare.global.config.fcm;

import java.util.Map;

import com.piuda.callcare.domain.notification.enums.NotificationType;

/**
 * FCM 푸시 발송 요청 — <b>한 요청 = 한 수신자</b>.
 * <p>
 * 알림 문구는 어르신마다 다르므로("○○ 어르신 혈압약이 3일치 남았습니다") 메시지와 수신자를 1:1로 묶는다.
 * 여러 명에게 보내야 하면 호출자가 수신자별로 반복 호출한다 — 수신자를 묶으면 한 사람의 문구가
 * 다른 보호자 기기로 나가는 오배송이 생긴다.
 *
 * @param type      알림 유형(이력 저장 및 클라이언트 분기용)
 * @param title     푸시 제목
 * @param body      푸시 본문(이력 Notification의 message로도 저장)
 * @param recipient 수신 대상(user+senior)
 * @param data      알림 탭 시 이동할 화면을 특정하기 위한 추가 값(딥링크용). 값은 전부 String이어야 하며,
 *                  id를 담을 때는 {@code String.valueOf()}로 감싼다. 어느 키가 필요한지는 알림 종류를 아는
 *                  호출자(트리거)가 정한다 — 발송 계층은 여기에 {@code type}과 {@code notificationId}만 얹는다.
 * @param targetMedicationId 이 알림이 가리키는 약(없으면 null). {@code data}에 실리는 딥링크 값과 달리
 *                  <b>이력 Notification에 저장</b>되며, 푸시가 아니라 <b>알림 센터 목록</b>에서 딥링크를
 *                  복원하는 데 쓰인다. {@code data}는 푸시 payload로만 나가고 DB에 남지 않기 때문에,
 *                  목록에서도 이동이 필요한 값은 여기로 따로 받는다.
 */
public record FcmSendRequest(
    NotificationType type,
    String title,
    String body,
    FcmRecipient recipient,
    Map<String, String> data,
    Long targetMedicationId
) {

    public FcmSendRequest {
        // 딥링크 키가 아직 없는 호출자를 위해 null을 허용하고 빈 맵으로 정규화한다.
        data = (data == null) ? Map.of() : Map.copyOf(data);
    }

    // 가리킬 약이 없는 알림(충돌·전화 미수신)용. 호출부가 의미 없는 null을 적지 않도록 오버로드로 둔다.
    public FcmSendRequest(NotificationType type, String title, String body, FcmRecipient recipient,
        Map<String, String> data) {
        this(type, title, body, recipient, data, null);
    }
}