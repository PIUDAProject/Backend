package com.piuda.callcare.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "약 단건 상세 응답 — 재등록 화면 프리필용")
public record MedicationDetailResponse(

        @Schema(description = "약 ID")
        Long medicationId,

        @Schema(description = "약 이름")
        String drugName,

        @Schema(description = "약 별명 (없으면 null)")
        String drugNickname,

        @Schema(description = "약 종류 (없으면 null)")
        String drugType,

        @Schema(description = "약 이미지 URL (없으면 null)")
        String imageUrl,

        @Schema(description = "1회 복용량 (예: 1정, 5ml)")
        String dosagePerTime,

        @Schema(description = "1일 복용 횟수")
        Integer timesPerDay,

        @Schema(description = "총 복용 일수 (없으면 null)")
        Integer totalDays,

        @Schema(description = "복용 시작일")
        LocalDate startDate,

        @Schema(description = "복용 종료일 (없으면 null)")
        LocalDate endDate,

        @Schema(description = "처방 날짜 (없으면 null)")
        LocalDate prescriptionDate,

        @Schema(description = "병원명 (없으면 null)")
        String hospitalName,

        @Schema(description = "복용법 + 보관법 — DrugInfo 연결 시에만 채워짐, 없으면 null")
        String usageStorageInfo,

        @Schema(description = "사용자 자유 입력 메모 (없으면 null)")
        String memo,

        @Schema(description = "복용 중 여부 — false면 종료된 약")
        Boolean isActive,

        @Schema(description = "연결된 DrugInfo ID — 재등록 시 drugInfoId 필드에 그대로 사용 (없으면 null)")
        Long drugInfoId
) {}
