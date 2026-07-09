package com.piuda.callcare.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "약물노트 그룹 카드 — 복용 시작일 + 병원 기준 묶음")
public record MedicationNoteGroupResponse(

        @Schema(description = "복용 시작일 (그룹 키)")
        LocalDate startDate,

        @Schema(description = "병원명 (없으면 null)")
        String hospitalName,

        @Schema(description = "그룹 내 약 리스트")
        List<MedicationNoteItemResponse> medications
) {}
