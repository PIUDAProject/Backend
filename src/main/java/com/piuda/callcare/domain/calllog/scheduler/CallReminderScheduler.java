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

    @Scheduled(cron = "40 * * * * *")
    public void notifyGuardiansForUnansweredCalls() {
        callReminderCommandService.notifyGuardiansForUnansweredCalls(LocalDateTime.now());
    }
}
