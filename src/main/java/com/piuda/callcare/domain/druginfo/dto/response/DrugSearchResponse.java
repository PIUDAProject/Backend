package com.piuda.callcare.domain.druginfo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약품 검색 결과")
public record DrugSearchResponse(

        @Schema(description = "품목기준코드")
        String itemSeq,

        @Schema(description = "약품명")
        String itemName,

        @Schema(description = "제조사명")
        String entpName,

        @Schema(description = "약 종류")
        String prductType,

        @Schema(description = "전문/일반 의약품 구분")
        String spcltyPblc,

        @Schema(description = "약 이미지 URL")
        String itemImage
) {
}
