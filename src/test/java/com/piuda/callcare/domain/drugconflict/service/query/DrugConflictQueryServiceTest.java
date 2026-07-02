package com.piuda.callcare.domain.drugconflict.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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

import com.piuda.callcare.domain.drugconflict.converter.DrugConflictConverter;
import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictDetailResponse;
import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictResponse;
import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;
import com.piuda.callcare.domain.drugconflict.repository.DrugConflictRepository;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("DrugConflictQueryService 단위 테스트")
class DrugConflictQueryServiceTest {

    private static final Long SENIOR_ID = 1L;

    @InjectMocks
    private DrugConflictQueryService drugConflictQueryService;

    @Mock
    private SeniorRepository seniorRepository;
    @Mock
    private DrugConflictRepository drugConflictRepository;
    @Spy
    private DrugConflictConverter drugConflictConverter = new DrugConflictConverter();

    @Test
    @DisplayName("충돌 목록을 금기(심각도 높은 순) 먼저 정렬해 반환한다")
    void getConflicts_sortedBySeverity() {
        // Given - 저장 순서는 주의 → 금기지만, 응답은 금기가 먼저 와야 함
        DrugConflict caution = conflict(1L, ConflictSeverity.CAUTION);
        DrugConflict contraindicated = conflict(2L, ConflictSeverity.CONTRAINDICATED);

        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(drugConflictRepository.findAllWithMedicationsBySeniorId(SENIOR_ID))
                .willReturn(List.of(caution, contraindicated));

        // When
        List<DrugConflictResponse> result = drugConflictQueryService.getConflicts(SENIOR_ID);

        // Then
        assertThat(result).extracting(DrugConflictResponse::severity)
                .containsExactly(ConflictSeverity.CONTRAINDICATED, ConflictSeverity.CAUTION);
        assertThat(result.get(0).severityLabel()).isEqualTo("금기");
    }

    @Test
    @DisplayName("충돌이 없으면 빈 목록을 반환한다")
    void getConflicts_empty() {
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(true);
        given(drugConflictRepository.findAllWithMedicationsBySeniorId(SENIOR_ID)).willReturn(List.of());

        assertThat(drugConflictQueryService.getConflicts(SENIOR_ID)).isEmpty();
    }

    @Test
    @DisplayName("예외: 어르신이 없으면 SENIOR_NOT_FOUND")
    void getConflicts_throws_whenSeniorNotFound() {
        given(seniorRepository.existsById(SENIOR_ID)).willReturn(false);

        assertThatThrownBy(() -> drugConflictQueryService.getConflicts(SENIOR_ID))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_NOT_FOUND);
    }

    @Test
    @DisplayName("상세: 존재하는 충돌을 반환한다")
    void getConflictDetail_returnsDetail() {
        DrugConflict conflict = conflict(5L, ConflictSeverity.CONTRAINDICATED);
        given(drugConflictRepository.findWithMedicationsById(5L)).willReturn(Optional.of(conflict));

        DrugConflictDetailResponse result = drugConflictQueryService.getConflictDetail(5L);

        assertThat(result.conflictId()).isEqualTo(5L);
        assertThat(result.severity()).isEqualTo(ConflictSeverity.CONTRAINDICATED);
    }

    @Test
    @DisplayName("예외: 충돌 상세가 없으면 DRUG_CONFLICT_NOT_FOUND")
    void getConflictDetail_throws_whenNotFound() {
        given(drugConflictRepository.findWithMedicationsById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> drugConflictQueryService.getConflictDetail(99L))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRUG_CONFLICT_NOT_FOUND);
    }

    // ---- fixtures ----

    private DrugConflict conflict(Long id, ConflictSeverity severity) {
        DrugConflict conflict = DrugConflict.builder()
                .medication1(medication(id * 10, "약A"))
                .medication2(medication(id * 10 + 1, "약B"))
                .severity(severity)
                .conflictDescription("설명")
                .build();
        ReflectionTestUtils.setField(conflict, "id", id);
        return conflict;
    }

    private Medication medication(Long id, String drugName) {
        Medication m = Medication.builder().drugName(drugName).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}