package com.piuda.callcare.domain.notification.service.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.notification.converter.NotificationConverter;
import com.piuda.callcare.domain.notification.dto.response.NotificationResponse;
import com.piuda.callcare.domain.notification.entity.Notification;
import com.piuda.callcare.domain.notification.repository.NotificationRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 알림 읽음 처리.
 * <p>
 * 클라이언트는 두 경로에서 이 API를 부른다 — 푸시를 탭했을 때(payload의 {@code notificationId})와
 * 알림 센터 목록에서 항목을 열었을 때. 두 경로가 같은 알림을 가리킬 수 있어 <b>여러 번 불려도
 * 안전해야 하고</b>, 그 멱등성은 {@link Notification#markAsRead()}가 보장한다(읽은 시각 미갱신).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationConverter notificationConverter;

    // 소유자 조건을 조회에 넣어 남의 알림을 읽음 처리할 수 없게 한다.
    // 남의 알림이면 NOT_FOUND가 나가는데, 403과 구분하지 않는 것이 의도다(id 존재 여부 노출 방지).
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead(); // dirty checking

        return notificationConverter.toResponse(notification);
    }
}
