package com.piuda.callcare.domain.ocrresult.dto;

import java.time.LocalDate;
import java.util.List;

public record OcrParseResult(
        String rawText,
        List<ParsedOcrData> parsedDrugs,
        LocalDate prescriptionDate  // 처방일 파싱 성공 시 채워짐, 실패 시 null
) {}
