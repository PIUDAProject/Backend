package com.piuda.callcare.domain.calllog.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.piuda.callcare.domain.notification.constant.NotificationDataKeys;
import com.piuda.callcare.domain.notification.enums.NotificationType;
import com.piuda.callcare.domain.notification.util.NotificationTimeCalculator;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.domain.senior.service.sms.SmsSender;
import com.piuda.callcare.global.config.fcm.FcmRecipient;
import com.piuda.callcare.global.config.fcm.FcmSendRecorder;
import com.piuda.callcare.global.config.fcm.FcmSendRequest;
import com.piuda.callcare.global.config.fcm.FcmSendResult;
import com.piuda.callcare.global.config.fcm.FcmSendService;
import com.piuda.callcare.global.config.fcm.FcmSendStatus;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallReminderCommandService {

    private static final List<CallStatus> NOTIFIABLE_STATUSES = List.of(CallStatus.PENDING, CallStatus.NO_ANSWER, CallStatus.FAILED);
    // 최초 발신 후 이 시간이 지나도록 수신되지 않으면 1회 재발신한다
    private static final int RETRY_DELAY_MINUTES = 10;
    // 재발신 대상으로 볼 최대 경과 시간. 스케줄러가 오래 멈춰 있었거나 재발신 도입 전에 쌓인
    // 과거 행까지 한꺼번에 발신하는 것을 막는다 — 한 시간 넘게 지난 복약 전화는 다시 걸지 않는다.
    private static final int RETRY_MAX_AGE_MINUTES = 60;
    // 마지막 발신(재발신 포함) 후 이 시간이 지나도록 수신되지 않으면 보호자·부모님에게 통보한다.
    // 재발신 시 calledAt이 갱신되므로 최초 발신 기준으로는 총 20분이다.
    private static final int GUARDIAN_SWEEP_OFFSET_MINUTES = 10;

    private final SeniorRepository seniorRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final CallLogRepository callLogRepository;
    private final VoiceCallSender voiceCallSender;
    private final SmsSender smsSender;
    private final MedicationLogCommandService medicationLogCommandService;
    private final FcmSendService fcmSendService;
    // 미수신 알림은 푸시 성공 여부와 무관하게 남아야 해서 이력 저장을 직접 부른다(발송 계층에 위임하지 않는다)
    private final FcmSendRecorder fcmSendRecorder;

    @Value("${coolsms.sender:}")
    private String senderNumber;

    public void sendDueFirstCalls(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        for (Senior senior : seniorRepository.findAll()) {
            if (!StringUtils.hasText(senior.getPhoneNumber())) {
                continue;
            }
            // 모든 시간대에 전화를 건다 — 어르신에게 대응 식사 시각이 있는 시간대가 전부이기 때문이다.
            for (MealTime mealTime : MealTime.values()) {
                try {
                    sendFirstCallIfDue(senior, mealTime, today, now);
                } catch (Exception e) {
                    log.error("전화 알림 발신 처리 실패 - seniorId={}, mealTime={}", senior.getId(), mealTime, e);
                }
            }
        }
    }

    // 재발신 스윕: 최초 발신 후 RETRY_DELAY_MINUTES가 지나도록 수신되지 않은 콜을 1회 더 발신한다.
    // 한 건의 실패가 스윕 전체를 멈추지 않도록 건별로 예외를 가둔다(sendDueFirstCalls와 동일 패턴).
    public void retryUnansweredCalls(LocalDateTime now) {
        LocalDateTime calledBefore = now.minusMinutes(RETRY_DELAY_MINUTES);
        LocalDateTime calledAfter = now.minusMinutes(RETRY_MAX_AGE_MINUTES);
        for (CallLog callLog : callLogRepository.findRetryTargets(NOTIFIABLE_STATUSES, calledAfter, calledBefore)) {
            try {
                retryCall(callLog, now);
            } catch (Exception e) {
                log.error("전화 알림 재발신 처리 실패 - callLogId={}, seniorId={}, mealTime={}",
                        callLog.getId(), callLog.getSenior().getId(), callLog.getMealTime(), e);
            }
        }
    }

    // 한 건의 실패가 스윕 전체를 멈추지 않도록 건별로 예외를 가둔다(다른 스윕 2개와 동일 패턴).
    // 통보는 선점 뒤에 이뤄지므로, 가두지 않으면 한 건의 오류가 그 분의 나머지 미수신 통보까지 막는다.
    public void notifyGuardiansForUnansweredCalls(LocalDateTime now) {
        LocalDateTime notificationThreshold = now.minusMinutes(GUARDIAN_SWEEP_OFFSET_MINUTES);
        for (CallLog callLog : callLogRepository.findGuardianNotificationTargets(NOTIFIABLE_STATUSES, notificationThreshold)) {
            try {
                notifyGuardian(callLog);
            } catch (Exception e) {
                log.error("미수신 통보 처리 실패 - callLogId={}, seniorId={}, mealTime={}",
                        callLog.getId(), callLog.getSenior().getId(), callLog.getMealTime(), e);
            }
        }
    }

    public String triggerMedicationCallForTest(Long seniorId, MealTime mealTime) {
        Senior senior = seniorRepository.findById(seniorId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        LocalDate today = LocalDate.now();
        if (!medicationScheduleRepository.existsUntakenScheduleForCall(seniorId, mealTime, today)) {
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
        // 미수신·발신 실패는 1차 콜이면 통보하지 않는다 — 재발신 스윕이 10분 뒤 한 번 더 걸기 때문.
        // 2차(마지막) 콜의 결과일 때만 즉시 통보하고, 웹훅이 오지 않는 경우는 통보 스윕이 받아준다.
        if (result == CallResult.NO_ANSWER) {
            if (callLog.markNoAnswer()) {
                callLogRepository.save(callLog);
                if (callLog.hasRetried()) {
                    notifyGuardian(callLog);
                }
            }
            return;
        }
        if (result == CallResult.FAILED) {
            if (callLog.markFailed()) {
                callLogRepository.save(callLog);
                if (callLog.hasRetried()) {
                    notifyGuardian(callLog);
                }
            }
        }
    }

    private void sendFirstCallIfDue(Senior senior, MealTime mealTime, LocalDate today, LocalDateTime now) {
        // 그 시간대에 아직 안 먹은 약이 있을 때만 발신한다 — 보호자가 이미 체크했으면 전화 자체를 걸지 않는다.
        if (!medicationScheduleRepository.existsUntakenScheduleForCall(senior.getId(), mealTime, today)) {
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

    // 재발신 1건. UNIQUE(senior_id, meal_time, call_date) 때문에 새 row를 만들 수 없으므로 같은 row를 갱신한다.
    // 발신하기로 정했으면 상태를 먼저 확정(선점)한 뒤 발신한다 — 재발신은 유실보다 중복이 나쁘기 때문
    // (중복은 곧 어르신에게 전화 2통). 선점으로 retryCount가 1이 되어 다음 스윕이 같은 row를 집지 않는다.
    private void retryCall(CallLog callLog, LocalDateTime now) {
        Senior senior = callLog.getSenior();
        MealTime mealTime = callLog.getMealTime();

        // 1차 발신 이후 보호자가 그 시간대를 체크했을 수 있다 — 그러면 재발신하지 않는다.
        if (!medicationScheduleRepository.existsUntakenScheduleForCall(senior.getId(), mealTime, callLog.getCallDate())) {
            skipRetry(callLog, "시간대 복약 완료");
            return;
        }

        if (!StringUtils.hasText(senior.getPhoneNumber())) {
            skipRetry(callLog, "부모님 전화번호 없음");
            return;
        }

        callLog.markRetryPreempted(now);
        callLogRepository.saveAndFlush(callLog);

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
            log.error("SOLAPI 전화 알림 재발신 실패 - callLogId={}, seniorId={}, mealTime={}",
                    callLog.getId(), senior.getId(), mealTime, e);
            return;
        }

        callLog.markSent(messageId, now);
        callLogRepository.save(callLog);
    }

    // 재발신하지 않기로 한 콜을 종료 상태로 돌린다 — 그냥 두면 재발신 스윕에 매분 다시 걸린다.
    private void skipRetry(CallLog callLog, String reason) {
        callLog.markSkipped();
        callLogRepository.save(callLog);
        log.info("전화 알림 재발신 생략({}) - callLogId={}, seniorId={}, mealTime={}",
                reason, callLog.getId(), callLog.getSenior().getId(), callLog.getMealTime());
    }

    private String bodyMessage(MealTime mealTime) {
        return mealTime.getDescription() + " 약을 복용할 시간입니다.";
    }

    private void completeMealTimeMedicationLogs(CallLog callLog) {
        // callDate가 이 콜이 대응하는 복약 날짜다(UNIQUE 키의 일부). 재발신으로 calledAt이 갱신되므로
        // 발신 시각이 아니라 callDate를 기준으로 삼아야 어느 날짜의 복약인지가 흔들리지 않는다.
        LocalDate date = callLog.getCallDate();
        medicationScheduleRepository.findActiveSchedulesForMealTime(
                        callLog.getSenior().getId(), callLog.getMealTime(), date)
                .forEach(schedule -> medicationLogCommandService.writeLog(
                        schedule.getMedication(), date, callLog.getMealTime(), true));
    }

    // 미수신 통보 — 재발신까지 모두 미수신인 "최종 상태"에서만 도달하는 지점이다(두 진입 경로 모두
    // retryCount >= 1을 이미 확인한다). 시도별 기록은 CallLog 상태머신이 갖고 있으므로 사용자 대면
    // 알림은 여기서 1건만 남긴다 — 미수신은 시도 이력이 아니라 최종 결과 통지다.
    private void notifyGuardian(CallLog callLog) {
        // 발송 "전에" 통보 권한을 선점한다. 실패하면 다른 경로가 이미 통보한 것이므로 조용히 빠진다.
        // 선점 후 발송은 이 파일의 재발신(markRetryPreempted)과 소진·충돌 알림이 쓰는 것과 같은 규칙이다
        // — 푸시·SMS는 회수할 수 없으니 한 번 놓치는 쪽이 두 번 보내는 쪽보다 낫다.
        if (callLogRepository.preemptNotification(callLog.getId()) == 0) {
            return;
        }
        callLog.markAsNotified(); // 벌크 UPDATE는 영속성 컨텍스트를 건너뛰므로 메모리 상태만 맞춰 준다

        // 인앱 알림은 발송 채널보다 먼저, 채널 성공 여부와 무관하게 남긴다. Notification은 알림 센터
        // 목록의 레코드이지 푸시 전달 기록이 아니다 — SMS로만 통보되거나 FCM이 미설정이어도 남아야 한다.
        //
        // 저장이 실패해도 통보는 계속한다. 선점이 이미 커밋돼 다음 스윕이 이 행을 다시 집지 않으므로,
        // 여기서 예외를 흘려보내면 미수신이 통째로 유실된다. 이력이 없으면 알림 센터에 안 남고 푸시의
        // 읽음 처리가 안 될 뿐이다 — 유실되면 안 되는 안전 알림이라 통보 전달을 기록보다 우선한다.
        FcmSendRequest pushRequest = missedCallPushRequest(callLog);
        Long notificationId = null;
        try {
            notificationId = fcmSendRecorder.saveNotification(pushRequest);
        } catch (Exception e) {
            log.error("미수신 알림 이력 저장 실패(통보는 계속) - seniorId={}, callLogId={}",
                    callLog.getSenior().getId(), callLog.getId(), e);
        }

        // 보호자는 앱을 쓰므로 FCM 푸시가 1순위다. 전달되지 못했으면(앱 미설치·전송 실패·FCM 미설정)
        // 미수신 통보는 유실되면 안 되는 안전 알림이라 SMS로 폴백한다.
        if (!notifyGuardianByPush(pushRequest, notificationId, callLog)) {
            String guardianPhoneNumber = callLog.getSenior().getUser().getPhoneNumber();
            if (StringUtils.hasText(guardianPhoneNumber)) {
                String guardianText = "[콜케어] " + callLog.getSenior().getName() + "님의 "
                        + callLog.getMealTime().getDescription()
                        + " 복약 전화 알림이 미수신되었습니다. 확인이 필요합니다.";
                try {
                    smsSender.send(senderNumber, guardianPhoneNumber, guardianText);
                } catch (Exception e) {
                    log.error("보호자 SMS 폴백 발송 실패 - seniorId={}, callLogId={}", callLog.getSenior().getId(), callLog.getId(), e);
                }
            } else {
                log.warn("보호자 푸시 미전달 + 전화번호 없음 - seniorId={}, callLogId={}",
                        callLog.getSenior().getId(), callLog.getId());
            }
        }

        String seniorPhoneNumber = callLog.getSenior().getPhoneNumber();
        if (StringUtils.hasText(seniorPhoneNumber)) {
            String seniorText = "[콜케어] " + callLog.getMealTime().getDescription()
                    + " 약 복용 전화 알림을 받지 못했습니다. 약 복용 여부를 확인해주세요.";
            try {
                smsSender.send(senderNumber, seniorPhoneNumber, seniorText);
            } catch (Exception e) {
                log.error("부모님 SMS 발송 실패 - seniorId={}, callLogId={}", callLog.getSenior().getId(), callLog.getId(), e);
            }
        } else {
            log.warn("부모님 전화번호 없음 - seniorId={}, callLogId={}", callLog.getSenior().getId(), callLog.getId());
        }
    }

    // 미수신 알림 1건의 내용. 이력 저장과 푸시 발송이 같은 값을 써야 하므로 한 곳에서 만든다.
    // FcmSendRequest는 "한 요청 = 한 수신자"라 다른 보호자에게 새지 않는다.
    //
    // 딥링크는 seniorId 하나뿐이다 — 미수신은 전용 상세 화면이 기획에 없어 어르신 홈으로 이동하고,
    // 그 이동에 필요한 값이 그것뿐이다(소진 → 약 노트, 충돌 → 리포트와 같은 규약).
    // type·notificationId는 모든 알림에 공통이라 발송 계층이 얹는다.
    private FcmSendRequest missedCallPushRequest(CallLog callLog) {
        Senior senior = callLog.getSenior();
        return new FcmSendRequest(
                NotificationType.MISSED_CALL,
                "복약 전화 미수신",
                senior.getName() + "님이 " + callLog.getMealTime().getDescription()
                        + " 복약 전화를 받지 않았습니다. 확인이 필요합니다.",
                new FcmRecipient(senior.getUser().getId(), senior.getId()),
                Map.of(NotificationDataKeys.SENIOR_ID, String.valueOf(senior.getId()))
        );
    }

    // 이력은 이미 저장했으므로 sendRecorded로 보낸다 — send를 쓰면 같은 알림이 한 번 더 저장된다.
    // FcmSendService는 트랜잭션 밖 호출이 계약인데 이 서비스에는 클래스 레벨 @Transactional이 없어 충족한다.
    private boolean notifyGuardianByPush(FcmSendRequest request, Long notificationId, CallLog callLog) {
        try {
            FcmSendResult result = fcmSendService.sendRecorded(request, notificationId);
            return result.status() == FcmSendStatus.SENT;
        } catch (Exception e) {
            log.error("보호자 FCM 푸시 발송 실패 - seniorId={}, callLogId={}",
                    callLog.getSenior().getId(), callLog.getId(), e);
            return false;
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
