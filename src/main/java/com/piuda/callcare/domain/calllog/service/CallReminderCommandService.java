package com.piuda.callcare.domain.calllog.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
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
                sendFirstCallIfDue(senior, mealTime, today, now);
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
            callLog.markAnswered();
            completeMealTimeMedicationLogs(callLog);
            return;
        }
        if (result == CallResult.NO_ANSWER) {
            callLog.markNoAnswer();
            notifyGuardian(callLog);
            return;
        }
        if (result == CallResult.FAILED) {
            callLog.markFailed();
            notifyGuardian(callLog);
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

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
        if (callLogRepository.existsBySenior_IdAndMealTimeAndCreatedAtBetween(
                senior.getId(), mealTime, startOfDay, endOfDay)) {
            return;
        }

        sendMedicationCall(senior, mealTime, now);
    }

    private String sendMedicationCall(Senior senior, MealTime mealTime, LocalDateTime calledAt) {
        if (!StringUtils.hasText(senior.getPhoneNumber())) {
            throw new CallCareException(ErrorCode.INVALID_PARAMETER);
        }

        String messageId = voiceCallSender.call(
                senderNumber,
                senior.getPhoneNumber(),
                "콜케어 복약 알림입니다.",
                bodyMessage(mealTime)
                );

        callLogRepository.save(CallLog.builder()
                .senior(senior)
                .mealTime(mealTime)
                .calledAt(calledAt)
                .status(CallStatus.PENDING)
                .retryCount(0)
                .messageId(messageId)
                .isNotified(false)
                .build());

        return messageId;
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

        String guardianPhoneNumber = callLog.getSenior().getUser().getPhoneNumber();
        if (!StringUtils.hasText(guardianPhoneNumber)) {
            log.warn("보호자 전화번호 없음 - seniorId={}, callLogId={}", callLog.getSenior().getId(), callLog.getId());
            callLog.markAsNotified();
            return;
        }

        String guardianText = "[콜케어] " + callLog.getSenior().getName() + "님의 "
                + callLog.getMealTime().getDescription()
                + " 복약 전화 알림이 미수신되었습니다. 확인이 필요합니다.";
        smsSender.send(senderNumber, guardianPhoneNumber, guardianText);

        String seniorPhoneNumber = callLog.getSenior().getPhoneNumber();
        if (StringUtils.hasText(seniorPhoneNumber)) {
            String seniorText = "[콜케어] " + callLog.getMealTime().getDescription()
                    + " 약 복용 전화 알림을 받지 못했습니다. 약 복용 여부를 확인해주세요.";
            smsSender.send(senderNumber, seniorPhoneNumber, seniorText);
        }
        callLog.markAsNotified();
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
