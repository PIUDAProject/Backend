package com.piuda.callcare.domain.medication.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import com.piuda.callcare.domain.medication.converter.MedicationConverter;
import com.piuda.callcare.domain.medication.dto.request.MedicationCreateRequest;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.medication.repository.MedicationScheduleRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationCommandService 단위 테스트 — 복용 횟수별 스케줄 생성")
class MedicationCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SENIOR_ID = 10L;

    @InjectMocks private MedicationCommandService medicationCommandService;

    @Mock private MedicationRepository medicationRepository;
    @Mock private MedicationScheduleRepository medicationScheduleRepository;
    @Mock private SeniorRepository seniorRepository;
    @Mock private DrugInfoRepository drugInfoRepository;
    @Mock private MedicationConverter medicationConverter;
    @Mock private ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("unchecked")
    private List<MealTime> registerAndCaptureMealTimes(Integer timesPerDay) {
        given(seniorRepository.findByIdAndUser_Id(SENIOR_ID, USER_ID)).willReturn(Optional.of(senior()));
        given(medicationRepository.save(any(Medication.class))).willAnswer(invocation -> invocation.getArgument(0));

        medicationCommandService.registerBatch(USER_ID, List.of(request(timesPerDay)));

        ArgumentCaptor<List<MedicationSchedule>> captor = ArgumentCaptor.forClass(List.class);
        then(medicationScheduleRepository).should(times(1)).saveAll(captor.capture());
        return captor.getValue().stream().map(MedicationSchedule::getMealTime).toList();
    }

    @Test
    @DisplayName("1일 1회는 아침 하나만 만든다")
    void once_createsBreakfastOnly() {
        assertThat(registerAndCaptureMealTimes(1)).containsExactly(MealTime.BREAKFAST);
    }

    @Test
    @DisplayName("1일 2회는 아침·저녁을 만든다 — 점심은 건너뛴다")
    void twice_createsBreakfastAndDinner() {
        assertThat(registerAndCaptureMealTimes(2)).containsExactly(MealTime.BREAKFAST, MealTime.DINNER);
    }

    @Test
    @DisplayName("1일 3회는 아침·점심·저녁을 만든다")
    void threeTimes_createsAllThree() {
        assertThat(registerAndCaptureMealTimes(3))
                .containsExactly(MealTime.BREAKFAST, MealTime.LUNCH, MealTime.DINNER);
    }

    @Test
    @DisplayName("1일 4회 이상도 아침·점심·저녁 3개가 상한이다")
    void fourOrMoreTimes_cappedAtThree() {
        assertThat(registerAndCaptureMealTimes(4))
                .containsExactly(MealTime.BREAKFAST, MealTime.LUNCH, MealTime.DINNER);
    }

    @Test
    @DisplayName("1일 5회도 4회와 같은 3개 시간대다 — 초과분은 표현하지 않는다(백로그)")
    void fiveTimes_sameAsFour() {
        assertThat(registerAndCaptureMealTimes(5))
                .containsExactly(MealTime.BREAKFAST, MealTime.LUNCH, MealTime.DINNER);
    }

    @Test
    @DisplayName("복용 횟수가 없으면 스케줄을 만들지 않는다")
    void nullTimesPerDay_createsNothing() {
        assertThat(registerAndCaptureMealTimes(null)).isEmpty();
    }

    // --- helpers ---

    private Senior senior() {
        return Senior.builder().name("김복순").build();
    }

    private MedicationCreateRequest request(Integer timesPerDay) {
        return new MedicationCreateRequest(
                SENIOR_ID, null, null, "혈압약", "1정", timesPerDay, 30,
                LocalDate.of(2026, 8, 20), null, "서울내과", null);
    }
}
