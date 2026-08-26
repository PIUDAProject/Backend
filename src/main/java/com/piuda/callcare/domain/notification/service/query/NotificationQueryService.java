package com.piuda.callcare.domain.notification.service.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.notification.converter.NotificationConverter;
import com.piuda.callcare.domain.notification.dto.response.NotificationResponse;
import com.piuda.callcare.domain.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 알림 센터 조회 — 보호자가 받은 알림을 최신순으로 내려준다.
 * <p>
 * 조회 단위가 어르신이 아니라 <b>보호자(userId)</b>인 이유는, 여러 어르신을 돌보는 보호자가 알림을 보려고
 * 화면을 여러 번 열지 않아야 하기 때문이다. userId는 토큰에서 오므로 이 값 자체가 소유권이다
 * (seniorId 기반 조회들이 소유권 검증을 TODO로 미뤄 둔 것과 다른 지점이다).
 * <p>
 * 딥링크 대상(약)이 살아 있는지는 확인하지 않는다 — 알림은 "그때 이런 일이 있었다"는 이력이고,
 * 목록을 그릴 때마다 약 생존을 확인하면 조인 비용만 늘 뿐 이미 발생한 사건이 달라지지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final NotificationConverter notificationConverter;

    // 알림이 없으면 빈 목록(프론트가 안내 문구 처리) — 충돌 목록과 같은 규약.
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findAllWithSeniorByUserId(userId).stream()
                .map(notificationConverter::toResponse)
                .toList();
    }
}
