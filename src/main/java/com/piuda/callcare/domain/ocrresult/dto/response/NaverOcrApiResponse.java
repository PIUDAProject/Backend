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
            String inferResult, // "SUCCESS" or "FAIL"
            List<Field> fields // 인식된 텍스트와 위치 정보
    ) {}

    public record Field(
            String inferText,
            Boolean lineBreak,
            BoundingPoly boundingPoly
    ) {}

    public record BoundingPoly(
            List<Vertex> vertices
    ) {}

    public record Vertex(
            Double x,
            Double y
    ) {}
}
