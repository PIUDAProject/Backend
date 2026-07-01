package com.piuda.callcare.domain.ocrresult.converter;

import com.piuda.callcare.domain.ocrresult.dto.response.OcrResultResponse;
import com.piuda.callcare.domain.ocrresult.entity.OcrResult;
import org.springframework.stereotype.Component;

@Component
public class OcrResultConverter {

    // OcrResult → OcrResultResponse
    public OcrResultResponse toResponse(OcrResult ocrResult) {
        return new OcrResultResponse(
                ocrResult.getId(),
                ocrResult.getParsedDrugName(),
                ocrResult.getParsedDosagePerTime(),
                ocrResult.getParsedTimesPerDay(),
                ocrResult.getParsedTotalDays(),
                ocrResult.getRawText()
        );
    }
}
