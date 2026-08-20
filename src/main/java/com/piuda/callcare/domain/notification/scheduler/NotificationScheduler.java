package com.piuda.callcare.domain.notification.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.notification.service.DepletionNotificationService;
import com.piuda.callcare.domain.notification.service.NotificationCleanupService;

import lombok.RequiredArgsConstructor;

/**
 * 알림 배치 — 소진 스캔(발송)과 보관 기간 만료 정리(삭제).
 * <p>
 * 충돌 알림은 여기에 없다. 충돌은 시간이 지나서 생기는 사건이 아니라 <b>약 구성이 바뀔 때</b>
 * 생기는 사건이라, 약 등록/수정과 리포트 진입 시점의 분석에 이벤트로 붙어 있다.
 */
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    // cron의 zone과 날짜 계산의 기준을 반드시 맞춘다. 서버 JVM이 UTC로 뜨면 LocalDate.now()가
    // 하루 어긋나 "오늘 잔여 3일"의 기준일이 밀린다 — 배치 시각과 대상 날짜는 같은 시간대여야 한다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DepletionNotificationService depletionNotificationService;
    private final NotificationCleanupService notificationCleanupService;

    // 오전 9시 — 푸시는 소리가 나므로 사람이 깨어 있는 시각에만 보낸다.
    // (병원 동기화 같은 새벽 배치에 얹으면 안 되는 이유다.)
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void notifyDepletingMedications() {
        depletionNotificationService.notifyDepletingMedications(LocalDate.now(KST));
    }

    // 정리는 사용자에게 보이지 않는 작업이라 트래픽이 적은 새벽에 돌린다.
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void deleteExpiredNotifications() {
        notificationCleanupService.deleteExpiredNotifications(LocalDateTime.now(KST));
    }
}
