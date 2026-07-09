package com.piuda.callcare.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "약물노트 그룹 카드 — 처방일 + 병원 기준 묶음")
public record MedicationNoteGroupResponse(

        @Schema(description = "처방일 (파싱 성공 시 처방일, 실패 시 복용 시작일 fallback — 그룹 키)")
        LocalDate prescriptionDate,

        @Schema(description = "병원명 (없으면 null)")
        String hospitalName,

        @Schema(description = "그룹 내 약 리스트")
        List<MedicationNoteItemResponse> medications
) {}
