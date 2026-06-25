package com.piuda.callcare.domain.hospital.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "병원 검색 결과")
public record HospitalSearchResponse(

        @Schema(description = "병원 ID")
        Long id,

        @Schema(description = "병원명")
        String name,

        @Schema(description = "주소")
        String address,

        @Schema(description = "전화번호")
        String phoneNumber
) {
}
