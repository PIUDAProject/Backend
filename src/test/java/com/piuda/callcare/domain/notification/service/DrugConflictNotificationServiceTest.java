package com.piuda.callcare.domain.notification.service;

import static com.piuda.callcare.domain.notification.fixture.NotificationFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;
import com.piuda.callcare.domain.drugconflict.event.DrugConflictDetectedEvent;
import com.piuda.callcare.domain.drugconflict.repository.DrugConflictRepository;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.notification.constant.NotificationDataKeys;
import com.piuda.callcare.domain.notification.enums.NotificationType;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.global.config.fcm.FcmSendRequest;
import com.piuda.callcare.global.config.fcm.FcmSendResult;
import com.piuda.callcare.global.config.fcm.FcmSendService;
import com.piuda.callcare.global.config.redis.IdempotencyKeyStore;

@ExtendWith(MockitoExtension.class)
@DisplayName("DrugConflictNotificationService 단위 테스트 — 약물 상호작용 푸시")
class DrugConflictNotificationServiceTest {

    @InjectMocks private DrugConflictNotificationService drugConflictNotificationService;

    @Mock private DrugConflictRepository drugConflictRepository;
    @Mock private MedicationScheduleRepository medicationScheduleRepository;
    @Mock private IdempotencyKeyStore idempotencyKeyStore;
    @Mock private FcmSendService fcmSendService;

    @Captor private ArgumentCaptor<FcmSendRequest> requestCaptor;
    @Captor private ArgumentCaptor<String> keyCaptor;

    @Test
    @DisplayName("정상 케이스: 겹치는 시간대가 있으면 위험 문구를 맨 앞에 두고 두 약 정보를 뒤에 붙인다")
    void notifyGuardian_highlights_overlapping_meal_time() {
        // Given
        Senior senior = createSenior();
        Medication first = createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 30);
        Medication second = createMedication(MEDICATION_2_ID, senior, "메트포르민", "당뇨약", "강남내과", 30);
        givenConflict(senior, first, second, ConflictSeverity.CONTRAINDICATED);
        given(medicationScheduleRepository.findAllByMedication_IdIn(any())).willReturn(List.of(
                createSchedule(first, MealTime.BREAKFAST),
                createSchedule(first, MealTime.DINNER),
                createSchedule(second, MealTime.DINNER)));
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(true);
        given(fcmSendService.send(any(FcmSendRequest.class))).willReturn(FcmSendResult.completed(1, 0, 0));

        // When
        drugConflictNotificationService.notifyGuardian(new DrugConflictDetectedEvent(CONFLICT_ID, false));

        // Then
        then(fcmSendService).should().send(requestCaptor.capture());
        FcmSendRequest request = requestCaptor.getValue();
        assertThat(request.type()).isEqualTo(NotificationType.DRUG_CONFLICT);
        assertThat(request.recipient().userId()).isEqualTo(USER_ID);
        // 잠금화면에서 잘려도 위험이 먼저 보이도록 선두에 온다
        assertThat(request.body()).startsWith("저녁에 함께 복용 위험");
        assertThat(request.body())
                .contains(SENIOR_NAME, "혈압약", "당뇨약", "서울내과", "강남내과", "아침·저녁");
        assertThat(request.data())
                .containsEntry(NotificationDataKeys.DRUG_CONFLICT_ID, String.valueOf(CONFLICT_ID));
    }

    @Test
    @DisplayName("정상 케이스: 복용 시간대가 겹치지 않아도 조합 위험은 그대로 알린다")
    void notifyGuardian_still_sends_when_meal_times_do_not_overlap() {
        // Given
        Senior senior = createSenior();
        Medication first = createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 30);
        Medication second = createMedication(MEDICATION_2_ID, senior, "메트포르민", "당뇨약", "강남내과", 30);
        givenConflict(senior, first, second, ConflictSeverity.CAUTION);
        given(medicationScheduleRepository.findAllByMedication_IdIn(any())).willReturn(List.of(
                createSchedule(first, MealTime.BREAKFAST),
                createSchedule(second, MealTime.DINNER)));
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(true);
        given(fcmSendService.send(any(FcmSendRequest.class))).willReturn(FcmSendResult.completed(1, 0, 0));

        // When
        drugConflictNotificationService.notifyGuardian(new DrugConflictDetectedEvent(CONFLICT_ID, false));

        // Then
        then(fcmSendService).should().send(requestCaptor.capture());
        assertThat(requestCaptor.getValue().body()).startsWith("복용 시간은 다르지만 함께 복용 시 주의");
    }

    @Test
    @DisplayName("정상 케이스: 멱등키가 충돌 행 id가 아니라 약 쌍+등급이라 약을 껐다 켜도 재발송되지 않는다")
    void notifyGuardian_keys_idempotency_by_medication_pair_and_severity() {
        // Given
        Senior senior = createSenior();
        Medication first = createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 30);
        Medication second = createMedication(MEDICATION_2_ID, senior, "메트포르민", "당뇨약", "강남내과", 30);
        givenConflict(senior, first, second, ConflictSeverity.CONTRAINDICATED);
        given(medicationScheduleRepository.findAllByMedication_IdIn(any())).willReturn(List.of());
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(true);
        given(fcmSendService.send(any(FcmSendRequest.class))).willReturn(FcmSendResult.completed(1, 0, 0));

        // When
        drugConflictNotificationService.notifyGuardian(new DrugConflictDetectedEvent(CONFLICT_ID, true));

        // Then
        // 행 id(CONFLICT_ID)가 아니라 약 쌍 id + 등급으로 잡힌다 — 행이 지워졌다 다시 생겨도 같은 키다
        then(idempotencyKeyStore).should().tryAcquire(keyCaptor.capture(), any(Duration.class));
        assertThat(keyCaptor.getValue())
                .isEqualTo("notify:conflict:" + MEDICATION_1_ID + ":" + MEDICATION_2_ID
                        + ":" + ConflictSeverity.CONTRAINDICATED.name())
                .doesNotContain(String.valueOf(CONFLICT_ID));
    }

    @Test
    @DisplayName("정상 케이스: 이미 같은 등급으로 보낸 조합이면 다시 보내지 않는다")
    void notifyGuardian_skips_when_already_notified() {
        // Given
        Senior senior = createSenior();
        Medication first = createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 30);
        Medication second = createMedication(MEDICATION_2_ID, senior, "메트포르민", "당뇨약", "강남내과", 30);
        givenConflict(senior, first, second, ConflictSeverity.CAUTION);
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(false);

        // When
        drugConflictNotificationService.notifyGuardian(new DrugConflictDetectedEvent(CONFLICT_ID, false));

        // Then
        then(fcmSendService).should(never()).send(any(FcmSendRequest.class));
    }

    @Test
    @DisplayName("예외 케이스: 발송 전에 약이 삭제·비활성화되면 보내지 않는다")
    void notifyGuardian_skips_when_conflict_no_longer_visible() {
        // Given
        given(drugConflictRepository.findForNotificationById(CONFLICT_ID)).willReturn(Optional.empty());

        // When
        drugConflictNotificationService.notifyGuardian(new DrugConflictDetectedEvent(CONFLICT_ID, false));

        // Then
        then(fcmSendService).should(never()).send(any(FcmSendRequest.class));
        then(idempotencyKeyStore).should(never()).tryAcquire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("예외 케이스: 발송이 실패해도 예외를 밖으로 던지지 않는다 (분석 결과는 이미 커밋됨)")
    void notifyGuardian_swallows_send_failure() {
        // Given
        Senior senior = createSenior();
        Medication first = createMedication(MEDICATION_1_ID, senior, "암로디핀", "혈압약", "서울내과", 30);
        Medication second = createMedication(MEDICATION_2_ID, senior, "메트포르민", "당뇨약", "강남내과", 30);
        givenConflict(senior, first, second, ConflictSeverity.CAUTION);
        given(medicationScheduleRepository.findAllByMedication_IdIn(any())).willReturn(List.of());
        given(idempotencyKeyStore.tryAcquire(anyString(), any(Duration.class))).willReturn(true);
        given(fcmSendService.send(any(FcmSendRequest.class))).willThrow(new RuntimeException("FCM 오류"));

        // When & Then — 예외가 전파되지 않는다
        drugConflictNotificationService.notifyGuardian(new DrugConflictDetectedEvent(CONFLICT_ID, false));
    }

    private void givenConflict(Senior senior, Medication first, Medication second, ConflictSeverity severity) {
        DrugConflict conflict = createConflict(senior, first, second, severity);
        given(drugConflictRepository.findForNotificationById(CONFLICT_ID)).willReturn(Optional.of(conflict));
    }
}
