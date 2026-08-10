package com.piuda.callcare.domain.drugconflict.dto.response;

import java.time.LocalDateTime;

import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;

import io.swagger.v3.oas.annotations.media.Schema;

// 약물 충돌 상세(카드 클릭 시). 충돌 설명 원문 포함.
// 약별 표시 정보는 목록 카드와 동일한 ConflictDrug 구조를 재사용한다.
@Schema(description = "약물 충돌 상세")
public record DrugConflictDetailResponse(

        @Schema(description = "충돌 ID") Long conflictId,
        @Schema(description = "약1 정보") ConflictDrug drug1,
        @Schema(description = "약2 정보") ConflictDrug drug2,
        @Schema(description = "심각도 (CONTRAINDICATED=금기, CAUTION=주의)") ConflictSeverity severity,
        @Schema(description = "심각도 한글 라벨") String severityLabel,
        @Schema(description = "충돌 설명 (상호작용 텍스트에서 추출한 문장)") String conflictDescription,
        @Schema(description = "확인(해결) 여부") boolean isResolved,
        @Schema(description = "분석 저장 시각") LocalDateTime createdAt
) {
}
