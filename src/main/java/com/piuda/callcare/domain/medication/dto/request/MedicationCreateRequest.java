package com.piuda.callcare.domain.medication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "약 등록 요청 — 카메라(OCR) / 직접 검색 공통")
public record MedicationCreateRequest(

        @Schema(description = "어르신 ID")
        @NotNull Long seniorId,

        @Schema(description = "OCR 경로 등록 시 ocrResultId, 직접 등록 시 null")
        Long ocrResultId,

        @Schema(description = "ES 검색으로 선택한 DrugInfo ID, 없으면 null")
        Long drugInfoId,

        @Schema(description = "약 이름 (OCR 자동 기입 또는 직접 입력)")
        @NotBlank String drugName,

        @Schema(description = "1회 복용량 (예: 1정, 5ml)")
        String dosagePerTime,

        @Schema(description = "1일 복용 횟수")
        @Min(1) Integer timesPerDay,

        @Schema(description = "총 복용 일수")
        @Min(1) Integer totalDays,

        @Schema(description = "복용 시작일")
        @NotNull LocalDate startDate,

        @Schema(description = "처방 날짜 (처방전 없으면 null → 등록일 기준)")
        LocalDate prescriptionDate,

        @Schema(description = "병원 ID — 병원 없이도 등록 가능 (null 허용)")
        Long hospitalId
) {}
