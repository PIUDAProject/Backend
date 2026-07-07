package com.piuda.callcare.domain.medication.dto.response;

import com.piuda.callcare.domain.medication.enums.MealTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "약 등록 결과")
public record MedicationResponse(

        @Schema(description = "등록된 약 ID")
        Long medicationId,

        @Schema(description = "병원 이름")
        String hospitalName,

        @Schema(description = "약 이름")
        String drugName,

        @Schema(description = "1회 복용량")
        String dosagePerTime,

        @Schema(description = "1일 복용 횟수")
        Integer timesPerDay,

        @Schema(description = "총 복용 일수")
        Integer totalDays,

        @Schema(description = "복용 시작일")
        LocalDate startDate,

        @Schema(description = "복용 종료일 (totalDays 기반 자동 계산)")
        LocalDate endDate,

        @Schema(description = "자동 생성된 복약 시간대 목록")
        List<MealTime> schedules
) {}
