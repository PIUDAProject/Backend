package com.piuda.callcare.domain.ocrresult.dto;

import java.util.List;

public record OcrParseResult(
        String rawText,
        List<ParsedOcrData> parsedDrugs
) {}
