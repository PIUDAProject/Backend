package com.piuda.callcare.domain.notification.dto.response;

import java.time.LocalDateTime;

import com.piuda.callcare.domain.notification.enums.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

// 알림 센터 목록 한 칸. 저장된 3종(전화 미수신·약 부족·약물 충돌)이 모두 이 형태로 내려간다.
//
// 표시값과 딥링크 조회키를 나눠 싣는다(9단계 규약). seniorName·typeLabel은 화면에 그리는 값이고,
// seniorId·medicationId는 화면 이동에 쓰는 값이다. 조회키는 원본을 그대로 둔다 —
// 가리키는 약이 나중에 삭제돼도 대체값으로 바꾸지 않는다.
@Schema(description = "알림 센터 항목")
public record NotificationResponse(

        @Schema(description = "알림 ID") Long notificationId,
        @Schema(description = "알림 유형 (MISSED_CALL=전화 미수신, LOW_STOCK=약 부족, DRUG_CONFLICT=약물 충돌)")
        NotificationType type,
        @Schema(description = "알림 유형 한글 라벨") String typeLabel,
        @Schema(description = "알림 내용 (발송된 푸시 본문과 동일)") String message,
        @Schema(description = "읽음 여부") boolean isRead,
        @Schema(description = "읽은 시각 (안 읽었으면 null)") LocalDateTime readAt,
        @Schema(description = "알림 발생 시각") LocalDateTime createdAt,
        @Schema(description = "어느 어르신에 대한 알림인지 (딥링크 조회키)") Long seniorId,
        @Schema(description = "어르신 이름 (표시값)") String seniorName,
        @Schema(description = "딥링크 대상 약 ID — LOW_STOCK만 채워지고 나머지 유형은 null. "
                + "해당 약이 삭제됐어도 원본 값을 그대로 내려준다.") Long medicationId
) {
}
