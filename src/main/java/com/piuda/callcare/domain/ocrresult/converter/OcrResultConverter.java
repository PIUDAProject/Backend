package com.piuda.callcare.domain.ocrresult.converter;

import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.OcrResultResponse;
import com.piuda.callcare.domain.ocrresult.entity.OcrResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class OcrResultConverter {

    // OcrResult + 파싱된 약 목록 + 처방일 → OcrResultResponse
    public OcrResultResponse toResponse(OcrResult ocrResult, List<ParsedOcrData> parsedDrugs, LocalDate prescriptionDate) {
        return new OcrResultResponse(
                ocrResult.getId(),
                ocrResult.getRawText(),
                parsedDrugs,
                prescriptionDate
        );
    }
}
