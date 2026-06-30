package com.piuda.callcare.domain.home.dto.response;

import java.util.List;

import com.piuda.callcare.domain.medication.enums.MealTime;

import io.swagger.v3.oas.annotations.media.Schema;

// 식사시간(BREAKFAST→LUNCH→DINNER ordinal 순) 단위 그룹
@Schema(description = "식사시간별 복약 그룹")
public record MealGroupResponse(

        @Schema(description = "식사 시간대") MealTime mealTime,
        @Schema(description = "해당 시간대 전체 복약 완료 여부 (오늘 전용)") boolean mealTimeCompleted,
        @Schema(description = "병원별 그룹 목록") List<HospitalGroupResponse> hospitalGroups
) {
}
