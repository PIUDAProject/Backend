package com.piuda.callcare.domain.calllog.service;

import static com.piuda.callcare.domain.calllog.fixture.CallLogFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.calllog.client.VoiceCallSender;
import com.piuda.callcare.domain.calllog.entity.CallLog;
import com.piuda.callcare.domain.calllog.enums.CallStatus;
import com.piuda.callcare.domain.calllog.repository.CallLogRepository;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.medicationlog.service.command.MedicationLogCommandService;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.domain.senior.service.sms.SmsSender;
import com.piuda.callcare.global.config.fcm.FcmSendRequest;
import com.piuda.callcare.global.config.fcm.FcmSendResult;
import com.piuda.callcare.global.config.fcm.FcmSendService;

@ExtendWith(MockitoExtension.class)
@DisplayName("CallReminderCommandService 단위 테스트 — 재발신 및 미수신 통보")
class CallReminderCommandServiceTest {

    @InjectMocks private CallReminderCommandService callReminderCommandService;

    @Mock private SeniorRepository seniorRepository;
    @Mock private MedicationScheduleRepository medicationScheduleRepository;
    @Mock private CallLogRepository callLogRepository;
    @Mock private VoiceCallSender voiceCallSender;
    @Mock private SmsSender smsSender;
    @Mock private MedicationLogCommandService medicationLogCommandService;
    @Mock private FcmSendService fcmSendService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(callReminderCommandService, "senderNumber", SENDER_NUMBER);
    }

    @Test
    @DisplayName("정상 케이스: 10분이 지나도록 수신되지 않으면 같은 row를 재발신 상태로 갱신한다")
    void retryUnansweredCalls_resends_when_delay_elapsed() {
        // Given
        CallLog callLog = createFirstCallLog(CallStatus.PENDING);
        given(callLogRepository.findRetryTargets(any(), eq(CALL_DATE), any())).willReturn(List.of(callLog));
        given(medicationScheduleRepository.existsUntakenScheduleForCall(SENIOR_ID, MEAL_TIME, CALL_DATE))
                .willReturn(true);
        given(voiceCallSender.call(eq(SENDER_NUMBER), eq(SENIOR_PHONE), anyString(), anyString()))
                .willReturn("message-2");

        // When
        callReminderCommandService.retryUnansweredCalls(RETRY_NOW);

        // Then
        assertThat(callLog.getRetryCount()).isEqualTo(1);
        assertThat(callLog.getStatus()).isEqualTo(CallStatus.PENDING);
        assertThat(callLog.getMessageId()).isEqualTo("message-2");
        assertThat(callLog.getCalledAt()).isEqualTo(RETRY_NOW);
        then(voiceCallSender).should(times(1)).call(eq(SENDER_NUMBER), eq(SENIOR_PHONE), anyString(), anyString());
    }

    @Test
    @DisplayName("정상 케이스: 재발신 직전 그 시간대가 이미 복약 완료면 발신하지 않고 SKIPPED로 종료한다")
    void retryUnansweredCalls_skips_when_meal_time_already_completed() {
        // Given
        CallLog callLog = createFirstCallLog(CallStatus.NO_ANSWER);
        given(callLogRepository.findRetryTargets(any(), eq(CALL_DATE), any())).willReturn(List.of(callLog));
        given(medicationScheduleRepository.existsUntakenScheduleForCall(SENIOR_ID, MEAL_TIME, CALL_DATE))
                .willReturn(false);

        // When
        callReminderCommandService.retryUnansweredCalls(RETRY_NOW);

        // Then
        assertThat(callLog.getStatus()).isEqualTo(CallStatus.SKIPPED);
        assertThat(callLog.getRetryCount()).isZero();
        then(voiceCallSender).should(never()).call(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("정상 케이스: 보호자가 이미 체크한 시간대는 최초 발신도 하지 않는다")
    void sendDueFirstCalls_skips_when_meal_time_already_completed() {
        // Given
        Senior senior = createSenior();
        given(seniorRepository.findAll()).willReturn(List.of(senior));
        given(medicationScheduleRepository.existsUntakenScheduleForCall(eq(SENIOR_ID), any(MealTime.class), any()))
                .willReturn(false);

        // When
        callReminderCommandService.sendDueFirstCalls(FIRST_CALLED_AT);

        // Then
        then(voiceCallSender).should(never()).call(anyString(), anyString(), anyString(), anyString());
        then(callLogRepository).should(never()).saveAndFlush(any(CallLog.class));
    }

    @Test
    @DisplayName("정상 케이스: 1차 콜의 미수신 결과로는 통보하지 않는다 (재발신 기회를 남긴다)")
    void applyCallResult_does_not_notify_on_first_call_no_answer() {
        // Given
        CallLog callLog = createFirstCallLog(CallStatus.PENDING);
        given(callLogRepository.findByMessageId("message-1")).willReturn(Optional.of(callLog));

        // When
        callReminderCommandService.applyCallResult("message-1", "NO_ANSWER");

        // Then
        assertThat(callLog.getStatus()).isEqualTo(CallStatus.NO_ANSWER);
        assertThat(callLog.getIsNotified()).isFalse();
        then(fcmSendService).should(never()).send(any(FcmSendRequest.class));
        then(smsSender).should(never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("정상 케이스: 재발신까지 미수신이면 보호자는 FCM, 부모님은 SMS로 통보한다")
    void applyCallResult_notifies_guardian_by_push_and_senior_by_sms() {
        // Given
        CallLog callLog = createRetriedCallLog(CallStatus.PENDING);
        given(callLogRepository.findByMessageId("message-1")).willReturn(Optional.of(callLog));
        given(fcmSendService.send(any(FcmSendRequest.class))).willReturn(FcmSendResult.completed(1, 0, 0));

        // When
        callReminderCommandService.applyCallResult("message-1", "NO_ANSWER");

        // Then
        then(fcmSendService).should(times(1)).send(any(FcmSendRequest.class));
        then(smsSender).should(times(1)).send(eq(SENDER_NUMBER), eq(SENIOR_PHONE), anyString());
        then(smsSender).should(never()).send(eq(SENDER_NUMBER), eq(GUARDIAN_PHONE), anyString());
        assertThat(callLog.getIsNotified()).isTrue();
    }

    @Test
    @DisplayName("정상 케이스: 보호자에게 푸시가 전달되지 않으면 SMS로 폴백한다")
    void applyCallResult_falls_back_to_sms_when_push_not_delivered() {
        // Given
        CallLog callLog = createRetriedCallLog(CallStatus.PENDING);
        given(callLogRepository.findByMessageId("message-1")).willReturn(Optional.of(callLog));
        given(fcmSendService.send(any(FcmSendRequest.class))).willReturn(FcmSendResult.noToken());

        // When
        callReminderCommandService.applyCallResult("message-1", "NO_ANSWER");

        // Then
        then(smsSender).should(times(1)).send(eq(SENDER_NUMBER), eq(GUARDIAN_PHONE), anyString());
        then(smsSender).should(times(1)).send(eq(SENDER_NUMBER), eq(SENIOR_PHONE), anyString());
        assertThat(callLog.getIsNotified()).isTrue();
    }

    @Test
    @DisplayName("정상 케이스: 전화를 정상 수신하면 그 시간대 약 전체를 복약 완료로 기록한다")
    void applyCallResult_completes_every_medication_in_meal_time() {
        // Given
        CallLog callLog = createRetriedCallLog(CallStatus.PENDING);
        Senior senior = callLog.getSenior();
        List<MedicationSchedule> schedules = List.of(
                createSchedule(senior, "혈압약"), createSchedule(senior, "당뇨약"));
        given(callLogRepository.findByMessageId("message-1")).willReturn(Optional.of(callLog));
        given(medicationScheduleRepository.findActiveSchedulesForMealTime(SENIOR_ID, MEAL_TIME, CALL_DATE))
                .willReturn(schedules);

        // When
        callReminderCommandService.applyCallResult("message-1", "ANSWERED");

        // Then
        assertThat(callLog.getStatus()).isEqualTo(CallStatus.ANSWERED);
        then(medicationLogCommandService).should(times(2))
                .writeLog(any(), eq(CALL_DATE), eq(MEAL_TIME), eq(true));
        then(fcmSendService).should(never()).send(any(FcmSendRequest.class));
    }

    @Test
    @DisplayName("예외 케이스: 재발신 발신이 실패하면 FAILED로 남고 재발신 횟수는 소진된다")
    void retryUnansweredCalls_marks_failed_when_send_throws() {
        // Given
        CallLog callLog = createFirstCallLog(CallStatus.NO_ANSWER);
        given(callLogRepository.findRetryTargets(any(), eq(CALL_DATE), any())).willReturn(List.of(callLog));
        given(medicationScheduleRepository.existsUntakenScheduleForCall(anyLong(), any(MealTime.class), any()))
                .willReturn(true);
        given(voiceCallSender.call(anyString(), anyString(), anyString(), anyString()))
                .willThrow(new RuntimeException("SOLAPI 오류"));

        // When
        callReminderCommandService.retryUnansweredCalls(RETRY_NOW);

        // Then
        assertThat(callLog.getStatus()).isEqualTo(CallStatus.FAILED);
        assertThat(callLog.getRetryCount()).isEqualTo(1);
    }
}
