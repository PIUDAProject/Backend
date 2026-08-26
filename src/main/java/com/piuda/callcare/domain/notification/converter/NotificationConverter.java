package com.piuda.callcare.domain.notification.converter;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.notification.dto.response.NotificationResponse;
import com.piuda.callcare.domain.notification.entity.Notification;

@Component
public class NotificationConverter {

    // Notification → NotificationResponse (알림 센터 목록·읽음 처리 응답용)
    // 유형 라벨은 저장하지 않고 enum에서 꺼낸다(파생값 비저장). 딥링크 조회키(seniorId·medicationId)는
    // 대체하지 않고 원본 그대로 싣는다 — 표시값만 대체하고 조회키는 원본을 유지하는 9단계 규약.
    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getType().getDescription(),
                notification.getMessage(),
                notification.getIsRead(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getSenior().getId(),
                notification.getSenior().getName(),
                notification.getMedicationId()
        );
    }
}
