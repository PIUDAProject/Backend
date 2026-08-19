package com.piuda.callcare.domain.notification.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 보관 기간이 지난 알림 정리 — 명세상 알림은 30일 보관 후 자동 삭제된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCleanupService {

    // 알림 보관 기간(명세)
    private static final int RETENTION_DAYS = 30;

    private final NotificationRepository notificationRepository;

    @Transactional
    public void deleteExpiredNotifications(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(RETENTION_DAYS);
        int deleted = notificationRepository.deleteAllCreatedBefore(threshold);
        log.info("보관 기간 만료 알림 삭제 - threshold={}, deleted={}", threshold, deleted);
    }
}
