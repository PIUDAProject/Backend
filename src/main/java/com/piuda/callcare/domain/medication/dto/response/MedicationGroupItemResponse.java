package com.piuda.callcare.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "그룹 내 약 단건 응답")
public record MedicationGroupItemResponse(

        @Schema(description = "약 ID")
        Long medicationId,

        @Schema(description = "약 이름")
        String drugName,

        @Schema(description = "약 종류 (없으면 null)")
        String drugType,

        @Schema(description = "약 이미지 URL (없으면 null)")
        String imageUrl,

        @Schema(description = "1일 복용 횟수")
        Integer timesPerDay,

        @Schema(description = "1회 복용량 (예: 1정, 5ml)")
        String dosagePerTime,

        @Schema(description = "복용 시작일")
        LocalDate startDate,

        @Schema(description = "복용 종료일 (없으면 null)")
        LocalDate endDate,

        @Schema(description = "총 복용 일수 (없으면 null)")
        Integer totalDays,

        @Schema(description = "메모 — OCR 자동 생성 또는 사용자 직접 입력 (없으면 null)")
        String memo
) {}
