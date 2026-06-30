package com.piuda.callcare.domain.ocrresult.dto.response;

import java.util.List;

public record NaverOcrApiResponse(
        String version,
        String requestId,
        Long timestamp,
        List<ImageResult> images
) {
    public record ImageResult(
            String uid,
            String name,
            String inferResult,
            List<Field> fields
    ) {}

    public record Field(
            String inferText,
            Boolean lineBreak
    ) {}
}
