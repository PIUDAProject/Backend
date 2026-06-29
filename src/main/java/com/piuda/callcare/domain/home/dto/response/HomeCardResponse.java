package com.piuda.callcare.domain.home.dto.response;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.piuda.callcare.domain.home.enums.HomeCardMode;

import io.swagger.v3.oas.annotations.media.Schema;

// 홈 복약 카드 최상위 응답
@Schema(description = "홈 복약 카드 응답")
public record HomeCardResponse(

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "조회 기준 날짜") LocalDate date,
        @Schema(description = "날짜 모드 (PAST/TODAY/FUTURE)") HomeCardMode mode,
        @Schema(description = "식사시간별 그룹 목록") List<MealGroupResponse> mealGroups
) {
}
