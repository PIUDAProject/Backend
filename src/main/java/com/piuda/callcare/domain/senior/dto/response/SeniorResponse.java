package com.piuda.callcare.domain.senior.dto.response;

import com.piuda.callcare.domain.senior.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "부모님 정보 응답")
public record SeniorResponse(

        @Schema(description = "부모님 ID") Long seniorId,
        @Schema(description = "성함") String name,
        @Schema(description = "성별") Gender gender,
        @Schema(description = "생년월일") LocalDate birthDate,
        @Schema(description = "전화번호") String phoneNumber,
        @Schema(description = "아침 식사 시간") LocalTime breakfastTime,
        @Schema(description = "점심 식사 시간") LocalTime lunchTime,
        @Schema(description = "저녁 식사 시간") LocalTime dinnerTime
) {
}
