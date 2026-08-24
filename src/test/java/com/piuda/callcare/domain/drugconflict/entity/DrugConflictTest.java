package com.piuda.callcare.domain.drugconflict.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;

@DisplayName("DrugConflict 단위 테스트 — 확인 상태와 등급 변화")
class DrugConflictTest {

    @Test
    @DisplayName("확인 처리하면 isResolved가 true가 된다")
    void resolve_marksResolved() {
        DrugConflict conflict = conflict(ConflictSeverity.CAUTION);

        conflict.resolve();

        assertThat(conflict.getIsResolved()).isTrue();
    }

    @Test
    @DisplayName("등급이 오르면 확인 상태가 풀려 목록에 다시 나온다")
    void updateAnalysis_escalation_resetsResolved() {
        // Given - 주의 등급을 보호자가 확인한 상태
        DrugConflict conflict = conflict(ConflictSeverity.CAUTION);
        conflict.resolve();

        // When - 재분석에서 금기로 올라감
        boolean escalated = conflict.updateAnalysis(ConflictSeverity.CONTRAINDICATED, "함께 복용하면 안 됩니다.");

        // Then - 확인한 것은 "그때의 등급"이므로 새 위험은 다시 확인받아야 한다
        assertThat(escalated).isTrue();
        assertThat(conflict.getIsResolved()).isFalse();
        assertThat(conflict.getSeverity()).isEqualTo(ConflictSeverity.CONTRAINDICATED);
    }

    @Test
    @DisplayName("등급이 내려가면 확인 상태를 유지한다 — 새 위험 정보가 아니다")
    void updateAnalysis_downgrade_keepsResolved() {
        // Given
        DrugConflict conflict = conflict(ConflictSeverity.CONTRAINDICATED);
        conflict.resolve();

        // When
        boolean escalated = conflict.updateAnalysis(ConflictSeverity.CAUTION, "주의가 필요합니다.");

        // Then
        assertThat(escalated).isFalse();
        assertThat(conflict.getIsResolved()).isTrue();
    }

    @Test
    @DisplayName("등급이 그대로면 설명이 바뀌어도 확인 상태를 유지한다")
    void updateAnalysis_sameSeverity_keepsResolved() {
        // Given
        DrugConflict conflict = conflict(ConflictSeverity.CAUTION);
        conflict.resolve();

        // When
        boolean escalated = conflict.updateAnalysis(ConflictSeverity.CAUTION, "설명만 바뀜");

        // Then
        assertThat(escalated).isFalse();
        assertThat(conflict.getIsResolved()).isTrue();
        assertThat(conflict.getConflictDescription()).isEqualTo("설명만 바뀜");
    }

    private DrugConflict conflict(ConflictSeverity severity) {
        return DrugConflict.builder()
                .severity(severity)
                .conflictDescription("초기 설명")
                .build();
    }
}
