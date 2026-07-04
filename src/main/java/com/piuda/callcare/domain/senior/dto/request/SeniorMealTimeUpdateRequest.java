package com.piuda.callcare.domain.senior.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

@Schema(description = "식사 시간 수정")
public record SeniorMealTimeUpdateRequest(

        @Schema(description = "아침 식사 시간", example = "08:00")
        @NotNull LocalTime breakfastTime,

        @Schema(description = "점심 식사 시간", example = "12:00")
        @NotNull LocalTime lunchTime,

        @Schema(description = "저녁 식사 시간", example = "18:00")
        @NotNull LocalTime dinnerTime
) {
}
