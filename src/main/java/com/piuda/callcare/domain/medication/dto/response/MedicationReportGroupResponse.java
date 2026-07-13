package com.piuda.callcare.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "복약 기록 리포트 병원별 그룹")
public record MedicationReportGroupResponse(

        @Schema(description = "병원명 (없으면 null)")
        String hospitalName,

        @Schema(description = "해당 병원의 복약 이력 목록 (연속 처방 합산 결과)")
        List<MedicationReportItemResponse> medications
) {}
