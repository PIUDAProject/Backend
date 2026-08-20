package com.piuda.callcare.domain.notification.service;

import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.piuda.callcare.domain.home.service.DepletionCalculator;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.notification.constant.NotificationDataKeys;
import com.piuda.callcare.domain.notification.enums.NotificationType;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.global.config.fcm.FcmSendRequest;
import com.piuda.callcare.global.config.fcm.FcmSendResult;
import com.piuda.callcare.global.config.fcm.FcmSendService;
import com.piuda.callcare.global.config.redis.IdempotencyKeyStore;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepletionNotificationService 단위 테스트 — 소진 임박 푸시")
class DepletionNotificationServiceTest {

    @InjectMocks private DepletionNotificationService depletionNotificationService;

    @Mock private MedicationRepository medicationRepository;
    @Spy private DepletionCalculator depletionCalculator = new DepletionCalculator();
    @Mock private IdempotencyKeyStore idempotencyKeyStore;
    @Mock private FcmSendService fcmSendService;

    @Captor private ArgumentCaptor<FcmSendRequest> requestCaptor;

    @Test
    @DisplayName("정상 케이스: 소진 임박 약마다 보호자에게 개별 푸시를 보낸다")
    void notifyDepletingMedications_sends_one_push_per_medication() {
        // Given
        Senior senior = createSenior();
        List<Medication> medications = List.of(
                createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 3),
                createMedication(MEDICATION_2_ID, senior, "메트포르민", "당뇨약", "강남내과", 1));
        given(medicationRepository.findDepletingForNotification(any(), any())).willReturn(medications);
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(true);
        given(fcmSendService.send(any(FcmSendRequest.class))).willReturn(FcmSendResult.completed(1, 0, 0));

        // When
        depletionNotificationService.notifyDepletingMedications(TODAY);

        // Then
        then(fcmSendService).should(times(2)).send(requestCaptor.capture());
        List<FcmSendRequest> sent = requestCaptor.getAllValues();
        assertThat(sent).allSatisfy(request -> {
            assertThat(request.type()).isEqualTo(NotificationType.LOW_STOCK);
            assertThat(request.recipient().userId()).isEqualTo(USER_ID);
            assertThat(request.recipient().seniorId()).isEqualTo(SENIOR_ID);
        });
        // 약 타러 갈 때 필요한 정보 — 별명·실제 약 이름·병원명이 모두 들어간다
        assertThat(sent.get(0).body()).contains(SENIOR_NAME, "혈압약", "암로디핀", "서울내과", "3일 남았습니다");
        assertThat(sent.get(0).data())
                .containsEntry(NotificationDataKeys.MEDICATION_ID, String.valueOf(MEDICATION_1_ID));
    }

    @Test
    @DisplayName("정상 케이스: 오늘이 마지막 복용일이면 남은 일수 대신 '오늘로 끝난다'고 알린다")
    void notifyDepletingMedications_says_last_day_when_no_days_left() {
        // Given
        Senior senior = createSenior();
        given(medicationRepository.findDepletingForNotification(any(), any()))
                .willReturn(List.of(createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 0)));
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(true);
        given(fcmSendService.send(any(FcmSendRequest.class))).willReturn(FcmSendResult.completed(1, 0, 0));

        // When
        depletionNotificationService.notifyDepletingMedications(TODAY);

        // Then
        then(fcmSendService).should().send(requestCaptor.capture());
        assertThat(requestCaptor.getValue().body()).contains("오늘로 끝납니다").doesNotContain("0일");
    }

    @Test
    @DisplayName("정상 케이스: 별명이 없으면 실제 약 이름을 앞에 내고 중복 표기하지 않는다")
    void notifyDepletingMedications_falls_back_to_drug_name_without_nickname() {
        // Given
        Senior senior = createSenior();
        given(medicationRepository.findDepletingForNotification(any(), any()))
                .willReturn(List.of(createMedication(MEDICATION_1_ID, senior, "암로디핀", null, "서울내과", 2)));
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(true);
        given(fcmSendService.send(any(FcmSendRequest.class))).willReturn(FcmSendResult.completed(1, 0, 0));

        // When
        depletionNotificationService.notifyDepletingMedications(TODAY);

        // Then
        then(fcmSendService).should().send(requestCaptor.capture());
        String body = requestCaptor.getValue().body();
        assertThat(body).contains("암로디핀(서울내과)");
        assertThat(body.indexOf("암로디핀")).isEqualTo(body.lastIndexOf("암로디핀")); // 이름이 두 번 나오지 않는다
    }

    @Test
    @DisplayName("정상 케이스: 같은 약을 같은 날 이미 보냈으면 다시 보내지 않는다")
    void notifyDepletingMedications_skips_when_already_sent_today() {
        // Given
        Senior senior = createSenior();
        given(medicationRepository.findDepletingForNotification(any(), any()))
                .willReturn(List.of(createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 3)));
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(false);

        // When
        depletionNotificationService.notifyDepletingMedications(TODAY);

        // Then
        then(fcmSendService).should(never()).send(any(FcmSendRequest.class));
    }

    @Test
    @DisplayName("정상 케이스: 조회 범위는 오늘부터 임계값(3일) 뒤까지다 — 종료된 약은 대상이 아니다")
    void notifyDepletingMedications_queries_only_from_today_to_threshold() {
        // Given
        given(medicationRepository.findDepletingForNotification(any(), any())).willReturn(List.of());

        // When
        depletionNotificationService.notifyDepletingMedications(TODAY);

        // Then
        then(medicationRepository).should()
                .findDepletingForNotification(eq(TODAY), eq(TODAY.plusDays(DepletionCalculator.DEPLETION_THRESHOLD_DAYS)));
    }

    @Test
    @DisplayName("예외 케이스: 한 건의 발송이 실패해도 나머지 약은 계속 발송한다")
    void notifyDepletingMedications_continues_when_one_send_throws() {
        // Given
        Senior senior = createSenior();
        given(medicationRepository.findDepletingForNotification(any(), any())).willReturn(List.of(
                createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 3),
                createMedication(MEDICATION_2_ID, senior, "메트포르민", "당뇨약", "강남내과", 1)));
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(true);
        given(fcmSendService.send(any(FcmSendRequest.class)))
                .willThrow(new RuntimeException("FCM 오류"))
                .willReturn(FcmSendResult.completed(1, 0, 0));

        // When
        depletionNotificationService.notifyDepletingMedications(TODAY);

        // Then
        then(fcmSendService).should(times(2)).send(any(FcmSendRequest.class));
    }
}
