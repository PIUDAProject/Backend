package com.piuda.callcare.domain.home.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.home.converter.HomeCardConverter;
import com.piuda.callcare.domain.home.dto.response.HomeCardResponse;
import com.piuda.callcare.domain.home.dto.response.MealGroupResponse;
import com.piuda.callcare.domain.home.enums.CompletedStatus;
import com.piuda.callcare.domain.home.enums.HomeCardMode;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.medicationlog.entity.MedicationLog;
import com.piuda.callcare.domain.medicationlog.repository.MedicationLogRepository;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeCardQueryService 단위 테스트")
class HomeCardQueryServiceTest {

    private static final Long SENIOR_ID = 1L;

    @InjectMocks
    private HomeCardQueryService homeCardQueryService;

    @Mock
    private SeniorRepository seniorRepository;
    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;
    @Mock
    private MedicationLogRepository medicationLogRepository;
    // 변환은 실제 동작을 검증하기 위해 spy 대신 실제 구현을 주입
    @org.mockito.Spy
    private HomeCardConverter homeCardConverter = new HomeCardConverter();
    // 완료 판정은 순수 로직이라 실제 구현을 주입
    @org.mockito.Spy
    private MealTimeCompletionCalculator completionCalculator = new MealTimeCompletionCalculator();

    private final LocalDate today = LocalDate.now();

    @Test
    @DisplayName("오늘: 식사시간/병원으로 그룹화하고 로그로 isTaken·mealTimeCompleted를 합성한다")
    void todayMode_groupsAndSynthesizesCompletion() {
        // Given - 아침: 서울내과(약10) + 병원없음(약11), 점심: 서울내과(약10)
        Medication med10 = medication(10L, "서울내과");
        Medication med11 = medication(11L, null);

        List<MedicationSchedule> schedules = List.of(
                schedule(med10, MealTime.BREAKFAST),
                schedule(med11, MealTime.BREAKFAST),
                schedule(med10, MealTime.LUNCH)
        );
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(medicationScheduleRepository.findActiveSchedulesForHomeCards(SENIOR_ID, today, today.plusDays(1).atStartOfDay())).willReturn(schedules);
        // 아침 약10 복용 완료(약11 미완료), 점심 약10 복용 완료
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, today))
                .willReturn(List.of(
                        log(med10, MealTime.BREAKFAST, true, today),
                        log(med10, MealTime.LUNCH, true, today)
                ));

        // When
        HomeCardResponse result = homeCardQueryService.getHomeCards(SENIOR_ID, today);

        // Then
        assertThat(result.mode()).isEqualTo(HomeCardMode.TODAY);
        assertThat(result.mealGroups()).hasSize(2); // BREAKFAST, LUNCH

        MealGroupResponse breakfast = result.mealGroups().get(0);
        assertThat(breakfast.mealTime()).isEqualTo(MealTime.BREAKFAST);
        assertThat(breakfast.hospitalGroups()).hasSize(2);
        // 약10은 taken=true, 약11은 taken=false → 시간대 미완료
        assertThat(breakfast.mealTimeCompleted()).isFalse();
        assertThat(breakfast.hospitalGroups().get(0).hospitalName()).isEqualTo("서울내과");
        assertThat(breakfast.hospitalGroups().get(0).medications().get(0).isTaken()).isTrue();
        assertThat(breakfast.hospitalGroups().get(1).hospitalId()).isNull();
        assertThat(breakfast.hospitalGroups().get(1).hospitalName()).isEqualTo("병원 정보 없음");
        assertThat(breakfast.hospitalGroups().get(1).medications().get(0).isTaken()).isFalse();

        // 점심은 약10 하나뿐이고 taken → 완료
        MealGroupResponse lunch = result.mealGroups().get(1);
        assertThat(lunch.mealTime()).isEqualTo(MealTime.LUNCH);
        assertThat(lunch.mealTimeCompleted()).isTrue();
    }

    @Test
    @DisplayName("미래: 완료 개념이 없어 로그를 조회하지 않고 isTaken/mealTimeCompleted가 모두 false다")
    void futureMode_noLogQuery_allFalse() {
        // Given
        LocalDate tomorrow = today.plusDays(1);
        Medication med10 = medication(10L, "서울내과");
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(medicationScheduleRepository.findActiveSchedulesForHomeCards(SENIOR_ID, tomorrow, tomorrow.plusDays(1).atStartOfDay()))
                .willReturn(List.of(schedule(med10, MealTime.BREAKFAST)));

        // When
        HomeCardResponse result = homeCardQueryService.getHomeCards(SENIOR_ID, tomorrow);

        // Then
        assertThat(result.mode()).isEqualTo(HomeCardMode.FUTURE);
        assertThat(result.mealGroups().get(0).mealTimeCompleted()).isFalse();
        assertThat(result.mealGroups().get(0).hospitalGroups().get(0).medications().get(0).isTaken()).isFalse();
        then(medicationLogRepository).should(never()).findBySenior_IdAndTakenDate(anyLong(), any());
    }

    @Test
    @DisplayName("과거: 각 약에 completedStatus(COMPLETED/INCOMPLETE)를 표시한다")
    void pastMode_setsCompletedStatus() {
        // Given
        LocalDate yesterday = today.minusDays(1);
        Medication med10 = medication(10L, "서울내과");
        Medication med11 = medication(11L, "서울내과");
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(medicationScheduleRepository.findActiveSchedulesForHomeCards(SENIOR_ID, yesterday, yesterday.plusDays(1).atStartOfDay()))
                .willReturn(List.of(schedule(med10, MealTime.DINNER), schedule(med11, MealTime.DINNER)));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, yesterday))
                .willReturn(List.of(log(med10, MealTime.DINNER, true, yesterday)));

        // When
        HomeCardResponse result = homeCardQueryService.getHomeCards(SENIOR_ID, yesterday);

        // Then
        assertThat(result.mode()).isEqualTo(HomeCardMode.PAST);
        var meds = result.mealGroups().get(0).hospitalGroups().get(0).medications();
        assertThat(meds).extracting("medicationId", "completedStatus")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, CompletedStatus.COMPLETED),
                        org.assertj.core.groups.Tuple.tuple(11L, CompletedStatus.INCOMPLETE)
                );
    }

    @Test
    @DisplayName("BEDTIME 스케줄은 ordinal 순서로 맨 뒤 그룹에 포함된다")
    void bedtimeSchedule_sortedLast() {
        // Given
        Medication med10 = medication(10L, null);
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(medicationScheduleRepository.findActiveSchedulesForHomeCards(SENIOR_ID, today, today.plusDays(1).atStartOfDay()))
                .willReturn(List.of(schedule(med10, MealTime.BEDTIME), schedule(med10, MealTime.BREAKFAST)));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, today)).willReturn(List.of());

        // When
        HomeCardResponse result = homeCardQueryService.getHomeCards(SENIOR_ID, today);

        // Then
        assertThat(result.mealGroups()).extracting(MealGroupResponse::mealTime)
                .containsExactly(MealTime.BREAKFAST, MealTime.BEDTIME);
    }

    @Test
    @DisplayName("예외: seniorId에 해당하는 어르신이 없으면 SENIOR_NOT_FOUND 예외가 발생한다")
    void seniorNotFound_throws() {
        // Given
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(false);

        // When / Then
        assertThatThrownBy(() -> homeCardQueryService.getHomeCards(SENIOR_ID, today))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_NOT_FOUND);
        then(medicationScheduleRepository).should(never()).findActiveSchedulesForHomeCards(anyLong(), any(), any());
    }

    // ---- fixtures ----

    private Medication medication(Long id, String hospitalName) {
        Medication m = Medication.builder()
                .hospitalName(hospitalName)
                .drugName("약" + id)
                .drugNickname("별명")
                .drugType("타입")
                .imageUrl("img")
                .dosagePerTime("1정")
                .timesPerDay(3)
                .startDate(today.minusDays(5))
                .endDate(today.plusDays(5))
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private MedicationSchedule schedule(Medication medication, MealTime mealTime) {
        return MedicationSchedule.builder().medication(medication).mealTime(mealTime).build();
    }

    private MedicationLog log(Medication medication, MealTime mealTime, boolean taken, LocalDate takenDate) {
        return MedicationLog.builder()
                .medication(medication)
                .mealTime(mealTime)
                .isTaken(taken)
                .takenDate(takenDate)
                .build();
    }
}
