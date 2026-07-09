package com.piuda.callcare.domain.medication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "약 수정 요청 — null 필드는 변경하지 않음")
public record MedicationUpdateRequest(

        @Schema(description = "약 이름 (null이면 변경 안 함)")
        String drugName,

        @Schema(description = "1회 복용량 (null이면 변경 안 함)")
        String dosagePerTime,

        @Schema(description = "1일 복용 횟수 (null이면 변경 안 함, 변경 시 스케줄 재생성)")
        Integer timesPerDay,

        @Schema(description = "총 복용 일수 (null이면 변경 안 함)")
        Integer totalDays,

        @Schema(description = "복용 시작일 (null이면 변경 안 함)")
        LocalDate startDate,

        @Schema(description = "병원명 (null이면 변경 안 함)")
        String hospitalName,

        @Schema(description = "메모 (null이면 변경 안 함)")
        String memo
) {}
