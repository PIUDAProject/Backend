package com.piuda.callcare.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약 상세 정보 응답 — DrugInfo 미연결 시 전 필드 null")
public record MedicationDetailResponse(

        @Schema(description = "약품명")
        String drugName,

        @Schema(description = "효능·효과 (없으면 null)")
        String efcyQesitm,

        @Schema(description = "용법·용량 (없으면 null)")
        String useMethodQesitm,

        @Schema(description = "주의사항 (없으면 null)")
        String atpnQesitm,

        @Schema(description = "부작용 (없으면 null)")
        String seQesitm
) {
}
