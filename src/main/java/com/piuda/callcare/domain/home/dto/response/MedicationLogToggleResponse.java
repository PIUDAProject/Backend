package com.piuda.callcare.domain.home.dto.response;

import com.piuda.callcare.domain.home.enums.CompletedStatus;
import com.piuda.callcare.domain.medication.enums.MealTime;

import io.swagger.v3.oas.annotations.media.Schema;

// 복약 완료 토글 응답: 토글된 약의 복용 여부 + 그 시간대의 재계산된 완료 상태(파생값)
@Schema(description = "복약 완료 토글 응답")
public record MedicationLogToggleResponse(

        @Schema(description = "약 ID") Long medicationId,
        @Schema(description = "식사 시간대") MealTime mealTime,
        @Schema(description = "토글 후 이 약의 복용 여부") boolean isTaken,
        @Schema(description = "토글 후 해당 시간대의 재계산된 완료 상태") CompletedStatus completedStatus
) {
}