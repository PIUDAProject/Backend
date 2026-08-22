package com.piuda.callcare.domain.home.dto.response;

import java.time.LocalDate;

import com.piuda.callcare.domain.home.enums.HomeCardMode;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// 홈 상단 요약 카드. 모드마다 의미 있는 값만 채우고 나머지는 응답에서 생략한다
// (오늘=예정+완료+다음복용 / 내일=예정만 / 어제=완료만).
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "홈 대시보드 요약")
public record HomeSummaryResponse(

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "조회 기준 날짜") LocalDate date,
        @Schema(description = "날짜 모드 (PAST/TODAY/FUTURE)") HomeCardMode mode,
        @Schema(description = "예정 시간대 수 (어제는 생략, 최대 3)") Integer scheduledCount,
        @Schema(description = "완료 시간대 수 (내일은 생략, 최대 3)") Integer completedCount,
        @Schema(description = "다음 복용 안내 (오늘 전용, 남은 복용이 없으면 생략)") NextDoseResponse nextDose
) {
}
