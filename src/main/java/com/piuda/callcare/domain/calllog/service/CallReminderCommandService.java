package com.piuda.callcare.domain.calllog.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.piuda.callcare.domain.calllog.client.VoiceCallSender;
import com.piuda.callcare.domain.calllog.entity.CallLog;
import com.piuda.callcare.domain.calllog.enums.CallStatus;
import com.piuda.callcare.domain.calllog.repository.CallLogRepository;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.medicationlog.service.command.MedicationLogCommandService;
import com.piuda.callcare.domain.notification.util.NotificationTimeCalculator;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.domain.senior.service.sms.SmsSender;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallReminderCommandService {

    private static final List<MealTime> CALL_MEAL_TIMES = List.of(
            MealTime.BREAKFAST, MealTime.LUNCH, MealTime.DINNER);
    private static final List<CallStatus> NOTIFIABLE_STATUSES = List.of(CallStatus.PENDING, CallStatus.NO_ANSWER, CallStatus.FAILED);
    private static final int GUARDIAN_SWEEP_OFFSET_MINUTES = 20;

    private final SeniorRepository seniorRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final CallLogRepository callLogRepository;
    private final VoiceCallSender voiceCallSender;
    private final SmsSender smsSender;
    private final MedicationLogCommandService medicationLogCommandService;

    @Value("${coolsms.sender:}")
    private String senderNumber;

    public void sendDueFirstCalls(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        for (Senior senior : seniorRepository.findAll()) {
            if (!StringUtils.hasText(senior.getPhoneNumber())) {
                continue;
            }
            for (MealTime mealTime : CALL_MEAL_TIMES) {
                try {
                    sendFirstCallIfDue(senior, mealTime, today, now);
                } catch (Exception e) {
                    log.error("전화 알림 발신 처리 실패 - seniorId={}, mealTime={}", senior.getId(), mealTime, e);
                }
            }
        }
    }

    public void notifyGuardiansForUnansweredCalls(LocalDateTime now) {
        LocalDateTime notificationThreshold = now.minusMinutes(GUARDIAN_SWEEP_OFFSET_MINUTES);
        callLogRepository.findGuardianNotificationTargets(NOTIFIABLE_STATUSES, notificationThreshold)
                .forEach(this::notifyGuardian);
    }

    public String triggerMedicationCallForTest(Long seniorId, MealTime mealTime) {
        validateCallMealTime(mealTime);
        Senior senior = seniorRepository.findById(seniorId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        LocalDate today = LocalDate.now();
        if (!medicationScheduleRepository.existsActiveScheduleForCall(seniorId, mealTime, today)) {
            throw new CallCareException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND);
        }

        return sendMedicationCall(senior, mealTime, LocalDateTime.now());
    }

    public void applyCallResult(String messageId, String rawStatus) {
        if (!StringUtils.hasText(messageId)) {
            log.warn("전화 알림 콜백 messageId 누락 - status={}", rawStatus);
            return;
        }

        CallLog callLog = callLogRepository.findByMessageId(messageId)
                .orElse(null);
        if (callLog == null) {
            log.warn("전화 알림 콜백에 매칭되는 CallLog 없음 - messageId={}, status={}", messageId, rawStatus);
            return;
        }

        CallResult result = CallResult.from(rawStatus);
        if (result == CallResult.ANSWERED) {
            if (callLog.markAnswered()) {
                completeMealTimeMedicationLogs(callLog);
                callLogRepository.save(callLog);
            }
            return;
        }
        if (result == CallResult.NO_ANSWER) {
            if (callLog.markNoAnswer()) {
                callLogRepository.save(callLog);
                notifyGuardian(callLog);
            }
            return;
        }
        if (result == CallResult.FAILED) {
            if (callLog.markFailed()) {
                callLogRepository.save(callLog);
                notifyGuardian(callLog);
            }
        }
    }

    private void sendFirstCallIfDue(Senior senior, MealTime mealTime, LocalDate today, LocalDateTime now) {
        if (!medicationScheduleRepository.existsActiveScheduleForCall(senior.getId(), mealTime, today)) {
            return;
        }

        LocalDateTime callTime = NotificationTimeCalculator.firstCallTime(senior, mealTime, today);
        if (callTime.isAfter(now)) {
            return;
        }

        sendMedicationCall(senior, mealTime, now);
    }

    private String sendMedicationCall(Senior senior, MealTime mealTime, LocalDateTime calledAt) {
        if (!StringUtils.hasText(senior.getPhoneNumber())) {
            throw new CallCareException(ErrorCode.INVALID_PARAMETER);
        }

        Optional<CallLog> preemptedCallLog = preemptCallLog(senior, mealTime, calledAt);
        if (preemptedCallLog.isEmpty()) {
            log.info("전화 알림 중복 발신 스킵 - seniorId={}, mealTime={}, callDate={}",
                    senior.getId(), mealTime, calledAt.toLocalDate());
            return null;
        }

        CallLog callLog = preemptedCallLog.get();
        String messageId;
        try {
            messageId = voiceCallSender.call(
                    senderNumber,
                    senior.getPhoneNumber(),
                    "콜케어 복약 알림입니다.",
                    bodyMessage(mealTime)
            );
        } catch (Exception e) {
            callLog.markFailed();
            callLogRepository.save(callLog);
            log.error("SOLAPI 전화 알림 발신 실패 - callLogId={}, seniorId={}, mealTime={}",
                    callLog.getId(), senior.getId(), mealTime, e);
            throw e;
        }

        callLog.markSent(messageId, calledAt);
        callLogRepository.save(callLog);
        return messageId;
    }

    private Optional<CallLog> preemptCallLog(Senior senior, MealTime mealTime, LocalDateTime calledAt) {
        try {
            return Optional.of(callLogRepository.saveAndFlush(CallLog.builder()
                    .senior(senior)
                    .mealTime(mealTime)
                    .calledAt(calledAt)
                    .callDate(calledAt.toLocalDate())
                    .status(CallStatus.PENDING)
                    .retryCount(0)
                    .messageId(null)
                    .isNotified(false)
                    .build()));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    private String bodyMessage(MealTime mealTime) {
        return mealTime.getDescription() + " 약을 복용할 시간입니다.";
    }

    private void completeMealTimeMedicationLogs(CallLog callLog) {
        LocalDate date = callLog.getCreatedAt().toLocalDate();
        medicationScheduleRepository.findActiveSchedulesForMealTime(
                        callLog.getSenior().getId(), callLog.getMealTime(), date)
                .forEach(schedule -> medicationLogCommandService.writeLog(
                        schedule.getMedication(), date, callLog.getMealTime(), true));
    }

    private void notifyGuardian(CallLog callLog) {
        if (Boolean.TRUE.equals(callLog.getIsNotified())) {
            return;
        }

        boolean hasRecipient = false;
        boolean sentAny = false;

        String guardianPhoneNumber = callLog.getSenior().getUser().getPhoneNumber();
        if (StringUtils.hasText(guardianPhoneNumber)) {
            hasRecipient = true;
            String guardianText = "[콜케어] " + callLog.getSenior().getName() + "님의 "
                    + callLog.getMealTime().getDescription()
                    + " 복약 전화 알림이 미수신되었습니다. 확인이 필요합니다.";
            try {
                smsSender.send(senderNumber, guardianPhoneNumber, guardianText);
                sentAny = true;
            } catch (Exception e) {
                log.error("보호자 SMS 발송 실패 - seniorId={}, callLogId={}", callLog.getSenior().getId(), callLog.getId(), e);
            }
        } else {
            log.warn("보호자 전화번호 없음 - seniorId={}, callLogId={}", callLog.getSenior().getId(), callLog.getId());
        }

        String seniorPhoneNumber = callLog.getSenior().getPhoneNumber();
        if (StringUtils.hasText(seniorPhoneNumber)) {
            hasRecipient = true;
            String seniorText = "[콜케어] " + callLog.getMealTime().getDescription()
                    + " 약 복용 전화 알림을 받지 못했습니다. 약 복용 여부를 확인해주세요.";
            try {
                smsSender.send(senderNumber, seniorPhoneNumber, seniorText);
                sentAny = true;
            } catch (Exception e) {
                log.error("부모님 SMS 발송 실패 - seniorId={}, callLogId={}", callLog.getSenior().getId(), callLog.getId(), e);
            }
        } else {
            log.warn("부모님 전화번호 없음 - seniorId={}, callLogId={}", callLog.getSenior().getId(), callLog.getId());
        }

        if (sentAny || !hasRecipient) {
            callLog.markAsNotified();
            callLogRepository.save(callLog);
        }
    }

    private void validateCallMealTime(MealTime mealTime) {
        if (!CALL_MEAL_TIMES.contains(mealTime)) {
            throw new CallCareException(ErrorCode.UNSUPPORTED_MEAL_TIME);
        }
    }

    private enum CallResult {
        ANSWERED,
        NO_ANSWER,
        FAILED,
        UNKNOWN;

        static CallResult from(String rawStatus) {
            if (!StringUtils.hasText(rawStatus)) {
                return UNKNOWN;
            }
            String normalized = rawStatus.toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
            if (normalized.contains("NO_ANSWER") || normalized.contains("MISSED") || normalized.contains("ABSENT")
                    || normalized.contains("TIMEOUT") || normalized.contains("BUSY")) {
                return NO_ANSWER;
            }
            if (normalized.contains("ANSWERED") || normalized.contains("ANSWER") || normalized.contains("SUCCESS")
                    || normalized.contains("COMPLETE") || normalized.equals("2000")) {
                return ANSWERED;
            }
            if (normalized.contains("FAIL") || normalized.contains("ERROR") || normalized.contains("CANCEL")) {
                return FAILED;
            }
            return UNKNOWN;
        }
    }
}
