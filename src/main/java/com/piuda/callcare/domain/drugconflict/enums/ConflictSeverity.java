package com.piuda.callcare.domain.drugconflict.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 약물 충돌 심각도. intrc_qesitm의 경고 문장 말투로 판정한다.
//   - CONTRAINDICATED(금기): "~하지 마십시오", "피하십시오/피하여야" 등 함께 복용 금지
//   - CAUTION(주의): "의사·약사와 상의하십시오", "주의하십시오" 등 병용 시 확인 필요
@Getter
@RequiredArgsConstructor
public enum ConflictSeverity {

    CONTRAINDICATED(2, "금기"),
    CAUTION(1, "주의");

    // 한 쌍이 여러 문장에 걸릴 때 더 센 등급을 고르기 위한 우선순위(클수록 심각)
    private final int priority;
    private final String label;
}