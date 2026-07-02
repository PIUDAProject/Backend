package com.piuda.callcare.domain.home.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

// 복약 부족(소진 임박) 카드 한 칸. 목록에 포함된 약은 모두 부족(남은 일수 <= 임계값)이므로 별도 부족 여부 플래그는 두지 않는다.
@Schema(description = "복약 부족 카드")
public record MedicationDepletionResponse(

        @Schema(description = "약 ID") Long medicationId,
        @Schema(description = "약 이름") String drugName,
        @Schema(description = "약 별명") String drugNickname,
        @Schema(description = "남은 복용 일수 (오늘=0, 이미 지났으면 음수)") long remainingDays,
        @Schema(description = "복용 종료일") LocalDate endDate
) {
}