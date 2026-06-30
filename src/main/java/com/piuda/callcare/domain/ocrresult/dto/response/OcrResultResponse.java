package com.piuda.callcare.domain.ocrresult.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OCR 분석 결과")
public record OcrResultResponse(

        @Schema(description = "OCR 결과 ID (약 등록 시 연결용)")
        Long ocrResultId,

        @Schema(description = "파싱된 약 이름 (인식 실패 시 null)")
        String drugName,

        @Schema(description = "1회 복용량 (예: 1정, 5ml)")
        String dosagePerTime,

        @Schema(description = "1일 복용 횟수")
        Integer timesPerDay,

        @Schema(description = "총 복용 일수")
        Integer totalDays,

        @Schema(description = "OCR 원본 텍스트")
        String rawText
) {}
