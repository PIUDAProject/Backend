package com.piuda.callcare.domain.home.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// 한 식사시간 안에서 병원 단위로 묶인 약 카드 묶음 (hospital null이면 "병원 정보 없음")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "병원별 복약 카드 그룹")
public record HospitalGroupResponse(

        @Schema(description = "병원명") String hospitalName,
        @Schema(description = "약 카드 목록") List<MedicationCardResponse> medications
) {
}
