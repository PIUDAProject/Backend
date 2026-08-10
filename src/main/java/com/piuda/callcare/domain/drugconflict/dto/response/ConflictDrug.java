package com.piuda.callcare.domain.drugconflict.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

// 충돌에 관련된 약 한 알의 표시 정보. 목록 카드와 상세가 같은 구조를 공유한다.
// 저장하지 않고 조회 시 Medication에서 조합한다(파생값 비저장 원칙).
@Schema(description = "충돌에 관련된 약 정보")
public record ConflictDrug(

        @Schema(description = "약(medication) ID") Long medicationId,
        @Schema(description = "제품 종류 (drug_type)") String drugType,
        @Schema(description = "제품명 (drug_name)") String drugName,
        @Schema(description = "약 별명 (drug_nickname)") String drugNickname,
        @Schema(description = "처방 기관 (hospital_name)") String hospitalName,
        @Schema(description = "처방 날짜 (prescription_date, 없으면 start_date로 폴백)") LocalDate prescriptionDate
) {
}
