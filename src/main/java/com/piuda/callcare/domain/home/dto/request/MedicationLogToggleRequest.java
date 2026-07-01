package com.piuda.callcare.domain.home.dto.request;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.piuda.callcare.domain.medication.enums.MealTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 복약 완료 토글 요청 (어느 시간대를 토글할지 + 대상 날짜)
@Schema(description = "복약 완료 토글 요청")
public record MedicationLogToggleRequest(

        @Schema(description = "토글할 식사 시간대", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        MealTime mealTime,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "토글 대상 날짜 (생략 시 오늘, 오늘만 허용)")
        LocalDate date
) {
}