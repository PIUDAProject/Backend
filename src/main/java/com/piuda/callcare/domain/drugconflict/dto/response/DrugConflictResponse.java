package com.piuda.callcare.domain.drugconflict.dto.response;

import com.piuda.callcare.domain.drugconflict.enums.ConflictSeverity;

import io.swagger.v3.oas.annotations.media.Schema;

// 약물 충돌 카드 1칸(목록용). 상세 설명은 상세 조회에서 제공.
@Schema(description = "약물 충돌 카드")
public record DrugConflictResponse(

        @Schema(description = "충돌 ID") Long conflictId,
        @Schema(description = "약1 ID") Long medicationId1,
        @Schema(description = "약1 이름") String drugName1,
        @Schema(description = "약2 ID") Long medicationId2,
        @Schema(description = "약2 이름") String drugName2,
        @Schema(description = "심각도 (CONTRAINDICATED=금기, CAUTION=주의)") ConflictSeverity severity,
        @Schema(description = "심각도 한글 라벨") String severityLabel,
        @Schema(description = "확인(해결) 여부") boolean isResolved
) {
}