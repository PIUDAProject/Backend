package com.piuda.callcare.domain.ocrresult.dto.response;

import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "OCR 분석 결과")
public record OcrResultResponse(

        @Schema(description = "OCR 결과 ID (약 등록 시 연결용)")
        Long ocrResultId,

        @Schema(description = "OCR 원본 텍스트")
        String rawText,

        @Schema(description = "파싱된 약 목록 — 단일 약이면 1개, 표 처방전이면 여러 개")
        List<ParsedOcrData> parsedDrugs,

        @Schema(description = "처방일 — 처방전에서 파싱 성공 시 채워짐, 실패 시 null (약 등록 시 prescriptionDate 기본값으로 사용)")
        LocalDate prescriptionDate
) {}
