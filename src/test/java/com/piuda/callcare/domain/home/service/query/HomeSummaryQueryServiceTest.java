package com.piuda.callcare.domain.home.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.home.converter.HomeSummaryConverter;
import com.piuda.callcare.domain.home.dto.response.HomeSummaryResponse;
import com.piuda.callcare.domain.home.enums.HomeCardMode;
import com.piuda.callcare.domain.home.service.MealTimeCompletionCalculator;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.medicationlog.entity.MedicationLog;
import com.piuda.callcare.domain.medicationlog.repository.MedicationLogRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeSummaryQueryService 단위 테스트")
class HomeSummaryQueryServiceTest {

    private static final Long SENIOR_ID = 1L;
    private static final LocalTime BREAKFAST_TIME = LocalTime.of(8, 0);
    private static final LocalTime LUNCH_TIME = LocalTime.of(12, 0);
    private static final LocalTime DINNER_TIME = LocalTime.of(18, 0);

    @InjectMocks
    private HomeSummaryQueryService homeSummaryQueryService;

    @Mock
    private SeniorRepository seniorRepository;
    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;
    @Mock
    private MedicationLogRepository medicationLogRepository;
    // 순수 로직/변환은 실제 구현을 주입
    @Spy
    private MealTimeCompletionCalculator completionCalculator = new MealTimeCompletionCalculator();
    @Spy
    private HomeSummaryConverter homeSummaryConverter = new HomeSummaryConverter();

    private final LocalDate today = LocalDate.now();

    @Test
    @DisplayName("오늘: 예정은 시간대 수, 완료는 그 시간대 약이 전부 완료된 것만 센다")
    void todayMode_countsScheduledAndCompletedByMealTime() {
        // Given - 아침(약10 완료), 점심(약10·약11 중 약10만 완료 = 부분 완료), 저녁(약10 미완료)
        Medication med10 = medication(10L);
        Medication med11 = medication(11L);
        givenSchedules(today, List.of(
                schedule(med10, MealTime.BREAKFAST),
                schedule(med10, MealTime.LUNCH),
                schedule(med11, MealTime.LUNCH),
                schedule(med10, MealTime.DINNER)
        ));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, today)).willReturn(List.of(
                log(med10, MealTime.BREAKFAST),
                log(med10, MealTime.LUNCH)
        ));

        // When
        HomeSummaryResponse result = homeSummaryQueryService.getSummary(SENIOR_ID, today, LocalTime.of(9, 0));

        // Then - 부분 완료인 점심은 완료로 세지 않는다
        assertThat(result.mode()).isEqualTo(HomeCardMode.TODAY);
        assertThat(result.scheduledCount()).isEqualTo(3);
        assertThat(result.completedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("내일: 예정만 내려가고 완료·다음 복용은 생략되며 로그를 조회하지 않는다")
    void futureMode_scheduledOnly() {
        // Given
        LocalDate tomorrow = today.plusDays(1);
        Medication med10 = medication(10L);
        givenSchedules(tomorrow, List.of(
                schedule(med10, MealTime.BREAKFAST),
                schedule(med10, MealTime.DINNER)
        ));

        // When
        HomeSummaryResponse result = homeSummaryQueryService.getSummary(SENIOR_ID, tomorrow, LocalTime.of(9, 0));

        // Then
        assertThat(result.mode()).isEqualTo(HomeCardMode.FUTURE);
        assertThat(result.scheduledCount()).isEqualTo(2);
        assertThat(result.completedCount()).isNull();
        assertThat(result.nextDose()).isNull();
        then(medicationLogRepository).should(never()).findBySenior_IdAndTakenDate(anyLong(), any());
    }

    @Test
    @DisplayName("어제: 완료만 내려가고 예정은 생략된다")
    void pastMode_completedOnly() {
        // Given - 아침 완료, 저녁 미완료
        LocalDate yesterday = today.minusDays(1);
        Medication med10 = medication(10L);
        givenSchedules(yesterday, List.of(
                schedule(med10, MealTime.BREAKFAST),
                schedule(med10, MealTime.DINNER)
        ));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, yesterday))
                .willReturn(List.of(log(med10, MealTime.BREAKFAST)));

        // When
        HomeSummaryResponse result = homeSummaryQueryService.getSummary(SENIOR_ID, yesterday, LocalTime.of(9, 0));

        // Then
        assertThat(result.mode()).isEqualTo(HomeCardMode.PAST);
        assertThat(result.scheduledCount()).isNull();
        assertThat(result.completedCount()).isEqualTo(1);
        assertThat(result.nextDose()).isNull();
    }

    @Test
    @DisplayName("다음 복용: 현재 시각 이후 가장 가까운 시간대를 고르고 나머지 약 수를 센다")
    void nextDose_picksNearestUpcomingMealTime() {
        // Given - 13시 기준: 아침·점심은 시각이 지났고 저녁(18시)만 남음. 저녁 약 3건
        Medication med10 = medication(10L);
        Medication med11 = medication(11L);
        Medication med12 = medication(12L);
        givenSchedules(today, List.of(
                schedule(med10, MealTime.BREAKFAST),
                schedule(med10, MealTime.LUNCH),
                schedule(med10, MealTime.DINNER),
                schedule(med11, MealTime.DINNER),
                schedule(med12, MealTime.DINNER)
        ));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, today)).willReturn(List.of());

        // When
        HomeSummaryResponse result = homeSummaryQueryService.getSummary(SENIOR_ID, today, LocalTime.of(13, 0));

        // Then - 시각이 지난 아침·점심은 미완료여도 다음 복용이 아니다
        assertThat(result.nextDose().mealTime()).isEqualTo(MealTime.DINNER);
        assertThat(result.nextDose().mealTimeDescription()).isEqualTo("저녁");
        assertThat(result.nextDose().drugName()).isEqualTo("약10");
        assertThat(result.nextDose().otherCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("다음 복용: 시간대 안에서 이미 완료한 약은 안내에서 빠진다")
    void nextDose_excludesAlreadyTakenMedicationsInMealTime() {
        // Given - 13시 기준 남은 시간대는 저녁뿐. 저녁 약 3건 중 첫 약(약10)만 미리 완료
        Medication med10 = medication(10L);
        Medication med11 = medication(11L);
        Medication med12 = medication(12L);
        givenSchedules(today, List.of(
                schedule(med10, MealTime.DINNER),
                schedule(med11, MealTime.DINNER),
                schedule(med12, MealTime.DINNER)
        ));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, today))
                .willReturn(List.of(log(med10, MealTime.DINNER)));

        // When
        HomeSummaryResponse result = homeSummaryQueryService.getSummary(SENIOR_ID, today, LocalTime.of(13, 0));

        // Then - 완료한 약10이 아니라 남은 약11이 안내되고, 나머지 수도 약12 하나만 센다
        assertThat(result.nextDose().mealTime()).isEqualTo(MealTime.DINNER);
        assertThat(result.nextDose().drugName()).isEqualTo("약11");
        assertThat(result.nextDose().otherCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다음 복용: 남은 시간대를 이미 다 완료했으면 null이다")
    void nextDose_nullWhenUpcomingMealTimesCompleted() {
        // Given - 13시 기준 남은 시간대는 저녁뿐인데 이미 완료
        Medication med10 = medication(10L);
        givenSchedules(today, List.of(schedule(med10, MealTime.DINNER)));
        given(medicationLogRepository.findBySenior_IdAndTakenDate(SENIOR_ID, today))
                .willReturn(List.of(log(med10, MealTime.DINNER)));

        // When
        HomeSummaryResponse result = homeSummaryQueryService.getSummary(SENIOR_ID, today, LocalTime.of(13, 0));

        // Then
        assertThat(result.completedCount()).isEqualTo(1);
        assertThat(result.nextDose()).isNull();
    }

    @Test
    @DisplayName("예외 케이스: 존재하지 않는 어르신이면 SENIOR_NOT_FOUND 예외가 발생한다")
    void seniorNotFound_throws() {
        // Given
        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> homeSummaryQueryService.getSummary(SENIOR_ID, today))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_NOT_FOUND);
        then(medicationScheduleRepository).should(never()).findActiveSchedulesForHomeCards(anyLong(), any(), any());
    }

    // ---- fixtures ----

    private void givenSchedules(LocalDate date, List<MedicationSchedule> schedules) {
        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior()));
        given(medicationScheduleRepository.findActiveSchedulesForHomeCards(SENIOR_ID, date, date.plusDays(1).atStartOfDay()))
                .willReturn(schedules);
    }

    private Senior senior() {
        return Senior.builder()
                .name("김복순")
                .breakfastTime(BREAKFAST_TIME)
                .lunchTime(LUNCH_TIME)
                .dinnerTime(DINNER_TIME)
                .build();
    }

    private Medication medication(Long id) {
        Medication m = Medication.builder()
                .drugName("약" + id)
                .drugNickname("별명" + id)
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

    private MedicationLog log(Medication medication, MealTime mealTime) {
        return MedicationLog.builder()
                .medication(medication)
                .mealTime(mealTime)
                .isTaken(true)
                .takenDate(today)
                .build();
    }
}
