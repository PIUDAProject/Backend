package com.piuda.callcare.domain.calllog.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.calllog.service.CallReminderCommandService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CallReminderScheduler {

    private final CallReminderCommandService callReminderCommandService;

    @Scheduled(cron = "0 * * * * *")
    public void sendDueFirstCalls() {
        callReminderCommandService.sendDueFirstCalls(LocalDateTime.now());
    }

    // 0초=최초 발신, 20초=재발신, 40초=보호자 통보 — 같은 분 안에서 초 단위로 분산시킨다
    @Scheduled(cron = "20 * * * * *")
    public void retryUnansweredCalls() {
        callReminderCommandService.retryUnansweredCalls(LocalDateTime.now());
    }

    @Scheduled(cron = "40 * * * * *")
    public void notifyGuardiansForUnansweredCalls() {
        callReminderCommandService.notifyGuardiansForUnansweredCalls(LocalDateTime.now());
    }
}
