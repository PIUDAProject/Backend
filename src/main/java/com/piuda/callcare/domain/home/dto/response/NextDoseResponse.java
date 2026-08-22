package com.piuda.callcare.domain.home.dto.response;

import com.piuda.callcare.domain.medication.enums.MealTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// 다음 복용 안내 (오늘 전용). "혈압약 외 2건"처럼 첫 약 하나와 나머지 개수만 내려준다.
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "다음 복용 안내")
public record NextDoseResponse(

        @Schema(description = "다음 복용 시간대") MealTime mealTime,
        @Schema(description = "시간대 표기 (아침/점심/저녁)") String mealTimeDescription,
        @Schema(description = "해당 시간대 첫 약 이름") String drugName,
        @Schema(description = "해당 시간대 첫 약 별명 (없으면 생략)") String drugNickname,
        @Schema(description = "같은 시간대의 나머지 약 수") int otherCount
) {
}
