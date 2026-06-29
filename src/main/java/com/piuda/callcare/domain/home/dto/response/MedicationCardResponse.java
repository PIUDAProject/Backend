package com.piuda.callcare.domain.home.dto.response;

import com.piuda.callcare.domain.home.enums.CompletedStatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// 약 한 칸(= MedicationSchedule 1행) 카드. 모드와 무관하게 단일 타입으로 통일하고 미사용 필드는 응답에서 생략
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "복약 카드")
public record MedicationCardResponse(

        @Schema(description = "약 ID") Long medicationId,
        @Schema(description = "약 이름") String drugName,
        @Schema(description = "약 별명") String drugNickname,
        @Schema(description = "약 종류") String drugType,
        @Schema(description = "약 이미지 URL") String imageUrl,
        @Schema(description = "1회 복용량") String dosagePerTime,
        @Schema(description = "1일 복용 횟수") Integer timesPerDay,
        @Schema(description = "복용 완료 여부 (오늘/미래, 과거는 null)") Boolean isTaken,
        @Schema(description = "복용 완료 상태 (과거 전용)") CompletedStatus completedStatus
) {
}
