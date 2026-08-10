package com.piuda.callcare.domain.drugconflict.dto.response;

import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;

import io.swagger.v3.oas.annotations.media.Schema;

// 약물 충돌 카드 1칸(목록용). 상세 설명은 상세 조회에서 제공.
// 약별 표시 정보는 ConflictDrug로 묶어 상세 응답과 같은 형태를 유지한다.
@Schema(description = "약물 충돌 카드")
public record DrugConflictResponse(

        @Schema(description = "충돌 ID") Long conflictId,
        @Schema(description = "약1 정보") ConflictDrug drug1,
        @Schema(description = "약2 정보") ConflictDrug drug2,
        @Schema(description = "심각도 (CONTRAINDICATED=금기, CAUTION=주의)") ConflictSeverity severity,
        @Schema(description = "심각도 한글 라벨") String severityLabel,
        @Schema(description = "확인(해결) 여부") boolean isResolved
) {
}
