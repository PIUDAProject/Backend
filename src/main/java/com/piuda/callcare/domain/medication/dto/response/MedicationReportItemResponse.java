package com.piuda.callcare.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "복약 기록 리포트 약 단건 — 연속 처방 합산 결과")
public record MedicationReportItemResponse(

        @Schema(description = "약 이름")
        String drugName,

        @Schema(description = "약 종류 (없으면 null)")
        String drugType,

        @Schema(description = "복용 시작일 (합산 시 첫 번째 처방의 startDate)")
        LocalDate startDate,

        @Schema(description = "복용 종료일 (합산 시 마지막 처방의 endDate, 없으면 null)")
        LocalDate endDate,

        @Schema(description = "총 복용 일수 (연속 합산된 값, 없으면 null)")
        Integer totalDays,

        @Schema(description = "표시 기준 날짜 — prescriptionDate가 있으면 처방일, 없으면 등록일(createdAt)")
        LocalDate reportDate
) {}
