package com.piuda.callcare.domain.drugconflict.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;
import com.piuda.callcare.domain.drugconflict.repository.DrugConflictRepository;
import com.piuda.callcare.domain.drugconflict.service.DrugConflictMatcher;
import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("DrugConflictCommandService 단위 테스트")
class DrugConflictCommandServiceTest {

    private static final Long SENIOR_ID = 1L;

    @InjectMocks
    private DrugConflictCommandService drugConflictCommandService;

    @Mock
    private SeniorRepository seniorRepository;
    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private DrugConflictRepository drugConflictRepository;
    // 매칭 순수 로직은 실제 구현을 주입
    @Spy
    private DrugConflictMatcher drugConflictMatcher = new DrugConflictMatcher();

    @Test
    @DisplayName("충돌하는 활성 약 쌍을 발견하면 정규화된 순서(작은 id가 medication1)로 저장한다")
    void analyze_savesConflict_forMatchingPair() {
        // Given - b(항히스타민제 분류)가 a의 '복용하지 마십시오' 경고문에 걸림 → 금기
        Senior senior = senior();
        Medication a = medication(20L, drugInfo("코감기약", null,
                "항히스타민제를 함유하는 내복약과 함께 복용하지 마십시오."));
        Medication b = medication(10L, drugInfo("알레르기약", "[01410]항히스타민제", null));

        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(medicationRepository.findActiveWithDrugInfoBySeniorId(SENIOR_ID)).willReturn(List.of(a, b));
        given(drugConflictRepository.existsBySenior_IdAndMedication1_IdAndMedication2_Id(anyLong(), anyLong(), anyLong()))
                .willReturn(false);

        // When
        drugConflictCommandService.analyze(SENIOR_ID);

        // Then
        ArgumentCaptor<DrugConflict> captor = ArgumentCaptor.forClass(DrugConflict.class);
        then(drugConflictRepository).should(times(1)).save(captor.capture());
        DrugConflict saved = captor.getValue();
        assertThat(saved.getMedication1().getId()).isEqualTo(10L); // 작은 id가 medication1
        assertThat(saved.getMedication2().getId()).isEqualTo(20L);
        assertThat(saved.getSeverity()).isEqualTo(ConflictSeverity.CONTRAINDICATED);
        assertThat(saved.getConflictDescription()).contains("항히스타민제");
    }

    @Test
    @DisplayName("이미 저장된 조합이면 중복 저장하지 않는다")
    void analyze_skipsAlreadySavedPair() {
        Senior senior = senior();
        Medication a = medication(20L, drugInfo("코감기약", null,
                "항히스타민제를 함유하는 내복약과 함께 복용하지 마십시오."));
        Medication b = medication(10L, drugInfo("알레르기약", "[01410]항히스타민제", null));

        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(medicationRepository.findActiveWithDrugInfoBySeniorId(SENIOR_ID)).willReturn(List.of(a, b));
        given(drugConflictRepository.existsBySenior_IdAndMedication1_IdAndMedication2_Id(anyLong(), anyLong(), anyLong()))
                .willReturn(true);

        drugConflictCommandService.analyze(SENIOR_ID);

        then(drugConflictRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("겹치는 계열/성분이 없으면 저장하지 않는다")
    void analyze_savesNothing_whenNoConflict() {
        Senior senior = senior();
        Medication a = medication(10L, drugInfo("비타민C정", "[03160]혼합비타민제", "상호작용 정보 없음."));
        Medication b = medication(20L, drugInfo("소화제", "[02330]효소제제", "상호작용 정보 없음."));

        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(medicationRepository.findActiveWithDrugInfoBySeniorId(SENIOR_ID)).willReturn(List.of(a, b));

        drugConflictCommandService.analyze(SENIOR_ID);

        then(drugConflictRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("예외: 어르신이 없으면 SENIOR_NOT_FOUND, 약 조회도 하지 않는다")
    void analyze_throws_whenSeniorNotFound() {
        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> drugConflictCommandService.analyze(SENIOR_ID))
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_NOT_FOUND);
        then(medicationRepository).should(never()).findActiveWithDrugInfoBySeniorId(anyLong());
    }

    // ---- fixtures ----

    private Senior senior() {
        Senior senior = Senior.builder().name("어르신").build();
        ReflectionTestUtils.setField(senior, "id", SENIOR_ID);
        return senior;
    }

    private DrugInfo drugInfo(String itemName, String prductType, String intrc) {
        return DrugInfo.builder()
                .itemName(itemName)
                .prductType(prductType)
                .intrcQesitm(intrc)
                .build();
    }

    private Medication medication(Long id, DrugInfo drugInfo) {
        Medication m = Medication.builder()
                .drugInfo(drugInfo)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}