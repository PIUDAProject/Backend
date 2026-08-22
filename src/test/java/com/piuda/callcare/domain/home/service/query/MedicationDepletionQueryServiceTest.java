package com.piuda.callcare.domain.home.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.home.converter.MedicationDepletionConverter;
import com.piuda.callcare.domain.home.dto.response.MedicationDepletionResponse;
import com.piuda.callcare.domain.home.service.DepletionCalculator;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationDepletionQueryService 단위 테스트")
class MedicationDepletionQueryServiceTest {

    private static final Long SENIOR_ID = 1L;

    @InjectMocks
    private MedicationDepletionQueryService medicationDepletionQueryService;

    @Mock
    private SeniorRepository seniorRepository;
    @Mock
    private MedicationRepository medicationRepository;
    // 순수 로직/변환은 실제 구현을 주입
    @Spy
    private MedicationDepletionConverter depletionConverter = new MedicationDepletionConverter();
    @Spy
    private DepletionCalculator depletionCalculator = new DepletionCalculator();

    private final LocalDate today = LocalDate.now();

    @Test
    @DisplayName("부족(0~3일)인 약만 남은 일수 오름차순으로 반환한다 — 이미 끝난 약은 제외")
    void returnsOnlyDepleting_sortedByRemainingAscending() {
        // Given - 남은 5일(정상), 3일(부족), 1일(부족), -1일(이미 지남 - 제외)
        Medication normal = medication(10L, today.plusDays(5));
        Medication depleting3 = medication(11L, today.plusDays(3));
        Medication depleting1 = medication(12L, today.plusDays(1));
        Medication overdue = medication(13L, today.minusDays(1));

        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(medicationRepository.findActiveMedicationsForDepletion(SENIOR_ID))
                .willReturn(List.of(normal, depleting3, depleting1, overdue));

        // When
        List<MedicationDepletionResponse> result =
                medicationDepletionQueryService.getDepletingMedications(SENIOR_ID);

        // Then - 정상(5일)과 이미 끝난 약(-1일)은 제외, 나머지는 남은 일수 오름차순
        assertThat(result).extracting(MedicationDepletionResponse::medicationId)
                .containsExactly(12L, 11L);
        assertThat(result).extracting(MedicationDepletionResponse::remainingDays)
                .containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("부족한 약이 없으면 빈 목록을 반환한다")
    void returnsEmpty_whenNoneDepleting() {
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(medicationRepository.findActiveMedicationsForDepletion(SENIOR_ID))
                .willReturn(List.of(medication(10L, today.plusDays(10))));

        List<MedicationDepletionResponse> result =
                medicationDepletionQueryService.getDepletingMedications(SENIOR_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("예외: seniorId에 해당하는 어르신이 없으면 SENIOR_NOT_FOUND 예외가 발생한다")
    void seniorNotFound_throws() {
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(false);

        assertThatThrownBy(() -> medicationDepletionQueryService.getDepletingMedications(SENIOR_ID))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_NOT_FOUND);
        then(medicationRepository).should(never()).findActiveMedicationsForDepletion(anyLong());
    }

    @Test
    @DisplayName("병원명·처방일이 없으면 표시값은 대체하고 딥링크용 값은 null 그대로 내려준다")
    void nullHospitalAndPrescription_displayFallback_butGroupKeysStayNull() {
        // Given - 병원명 null, 처방일 null (복용 시작일만 있음)
        Medication med = medication(10L, today.plusDays(1));
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(medicationRepository.findActiveMedicationsForDepletion(SENIOR_ID)).willReturn(List.of(med));

        // When
        MedicationDepletionResponse result = medicationDepletionQueryService.getDepletingMedications(SENIOR_ID).get(0);

        // Then - 표시용은 채워지고, 그룹 조회 키는 원본 null을 유지해야 딥링크가 IS NULL 그룹을 찾는다
        assertThat(result.hospitalName()).isEqualTo("병원 정보 없음");
        assertThat(result.prescriptionDate()).isEqualTo(today.minusDays(5));
        assertThat(result.groupHospitalName()).isNull();
        assertThat(result.groupPrescriptionDate()).isNull();
    }

    @Test
    @DisplayName("병원명·처방일이 있으면 표시값과 딥링크용 값이 모두 원본이다")
    void hospitalAndPrescriptionPresent_bothUseOriginal() {
        // Given
        Medication med = medication(11L, today.plusDays(2));
        ReflectionTestUtils.setField(med, "hospitalName", "서울내과");
        ReflectionTestUtils.setField(med, "prescriptionDate", today.minusDays(3));
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(medicationRepository.findActiveMedicationsForDepletion(SENIOR_ID)).willReturn(List.of(med));

        // When
        MedicationDepletionResponse result = medicationDepletionQueryService.getDepletingMedications(SENIOR_ID).get(0);

        // Then
        assertThat(result.hospitalName()).isEqualTo("서울내과");
        assertThat(result.groupHospitalName()).isEqualTo("서울내과");
        assertThat(result.prescriptionDate()).isEqualTo(today.minusDays(3));
        assertThat(result.groupPrescriptionDate()).isEqualTo(today.minusDays(3));
    }

    // ---- fixtures ----

    private Medication medication(Long id, LocalDate endDate) {
        Medication m = Medication.builder()
                .drugName("약" + id)
                .drugNickname("별명" + id)
                .startDate(today.minusDays(5))
                .endDate(endDate)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}