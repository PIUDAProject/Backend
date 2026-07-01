package com.piuda.callcare.domain.home.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.home.dto.response.MedicationLogToggleResponse;
import com.piuda.callcare.domain.home.enums.CompletedStatus;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.medicationlog.entity.MedicationLog;
import com.piuda.callcare.domain.medicationlog.repository.MedicationLogRepository;
import com.piuda.callcare.domain.medicationlog.service.command.MedicationLogCommandService;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationToggleCommandService 단위 테스트")
class MedicationToggleCommandServiceTest {

    private static final Long SENIOR_ID = 1L;
    private static final Long MED_ID = 10L;

    @InjectMocks
    private MedicationToggleCommandService medicationToggleCommandService;

    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;
    @Mock
    private MedicationLogRepository medicationLogRepository;
    @Mock
    private MedicationLogCommandService medicationLogCommandService;
    // 완료 판정은 순수 로직이라 실제 구현을 주입
    @org.mockito.Spy
    private MealTimeCompletionCalculator completionCalculator = new MealTimeCompletionCalculator();

    private final LocalDate today = LocalDate.now();

    @Test
    @DisplayName("첫 체크: 기존 로그가 없으면 isTaken=true로 쓰고, 그 시간대가 완료되면 COMPLETED를 반환한다")
    void toggle_firstCheck_insertsTrueAndCompleted() {
        // Given - 약 1개뿐인 시간대, 토글 후 그 약이 복용 완료
        Medication med = medication(MED_ID);
        given(medicationRepository.findById(MED_ID)).willReturn(Optional.of(med));
        given(medicationScheduleRepository.existsByMedication_IdAndMealTime(MED_ID, MealTime.BREAKFAST))
                .willReturn(true);
        given(medicationLogRepository.findByMedication_IdAndTakenDateAndMealTime(MED_ID, today, MealTime.BREAKFAST))
                .willReturn(Optional.empty());
        // 재계산: 그 시간대 스케줄 = 약1개, 로그 = 복용완료
        given(medicationScheduleRepository.findActiveSchedulesForHomeCards(SENIOR_ID, today))
                .willReturn(List.of(schedule(med, MealTime.BREAKFAST)));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, today))
                .willReturn(List.of(log(med, MealTime.BREAKFAST, true)));

        // When
        MedicationLogToggleResponse result =
                medicationToggleCommandService.toggle(MED_ID, MealTime.BREAKFAST, null);

        // Then
        assertThat(result.isTaken()).isTrue();
        assertThat(result.completedStatus()).isEqualTo(CompletedStatus.COMPLETED);
        then(medicationLogCommandService).should().writeLog(med, today, MealTime.BREAKFAST, true);
    }

    @Test
    @DisplayName("해제: 기존 로그가 복용완료면 isTaken=false로 쓰고, 시간대 미완료면 INCOMPLETE를 반환한다")
    void toggle_uncheck_writesFalseAndIncomplete() {
        // Given
        Medication med = medication(MED_ID);
        given(medicationRepository.findById(MED_ID)).willReturn(Optional.of(med));
        given(medicationScheduleRepository.existsByMedication_IdAndMealTime(MED_ID, MealTime.DINNER))
                .willReturn(true);
        given(medicationLogRepository.findByMedication_IdAndTakenDateAndMealTime(MED_ID, today, MealTime.DINNER))
                .willReturn(Optional.of(log(med, MealTime.DINNER, true)));
        // 재계산: 해제 후 로그는 미복용
        given(medicationScheduleRepository.findActiveSchedulesForHomeCards(SENIOR_ID, today))
                .willReturn(List.of(schedule(med, MealTime.DINNER)));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, today))
                .willReturn(List.of(log(med, MealTime.DINNER, false)));

        // When
        MedicationLogToggleResponse result =
                medicationToggleCommandService.toggle(MED_ID, MealTime.DINNER, null);

        // Then
        assertThat(result.isTaken()).isFalse();
        assertThat(result.completedStatus()).isEqualTo(CompletedStatus.INCOMPLETE);
        then(medicationLogCommandService).should().writeLog(med, today, MealTime.DINNER, false);
    }

    @Test
    @DisplayName("예외: 오늘이 아닌 날짜(과거)는 토글할 수 없다")
    void toggle_throws_whenNotToday() {
        LocalDate yesterday = today.minusDays(1);

        assertThatThrownBy(() -> medicationToggleCommandService.toggle(MED_ID, MealTime.BREAKFAST, yesterday))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_LOG_TOGGLE_NOT_TODAY);
        then(medicationRepository).should(never()).findById(anyLong());
        then(medicationLogCommandService).should(never()).writeLog(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("예외: 약이 없으면 MEDICATION_NOT_FOUND")
    void toggle_throws_whenMedicationNotFound() {
        given(medicationRepository.findById(MED_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> medicationToggleCommandService.toggle(MED_ID, MealTime.BREAKFAST, null))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("예외: 그 시간대 스케줄이 없으면 MEDICATION_SCHEDULE_NOT_FOUND")
    void toggle_throws_whenScheduleNotFound() {
        Medication med = medication(MED_ID);
        given(medicationRepository.findById(MED_ID)).willReturn(Optional.of(med));
        given(medicationScheduleRepository.existsByMedication_IdAndMealTime(MED_ID, MealTime.LUNCH))
                .willReturn(false);

        assertThatThrownBy(() -> medicationToggleCommandService.toggle(MED_ID, MealTime.LUNCH, null))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND);
        then(medicationLogCommandService).should(never()).writeLog(any(), any(), any(), anyBoolean());
    }

    // ---- fixtures ----

    private Senior senior() {
        Senior s = Senior.builder().name("김복순").build();
        ReflectionTestUtils.setField(s, "id", SENIOR_ID);
        return s;
    }

    private Medication medication(Long id) {
        Medication m = Medication.builder()
                .senior(senior())
                .drugName("약" + id)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private MedicationSchedule schedule(Medication medication, MealTime mealTime) {
        return MedicationSchedule.builder().medication(medication).mealTime(mealTime).build();
    }

    private MedicationLog log(Medication medication, MealTime mealTime, boolean taken) {
        return MedicationLog.builder()
                .medication(medication)
                .senior(medication.getSenior())
                .mealTime(mealTime)
                .isTaken(taken)
                .takenDate(today)
                .build();
    }
}