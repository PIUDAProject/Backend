package com.piuda.callcare.domain.home.dto.response;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

// 복약 부족(소진 임박) 카드 한 칸. 목록에 포함된 약은 모두 부족(남은 일수 <= 임계값)이므로 별도 부족 여부 플래그는 두지 않는다.
//
// 병원명·처방일은 "화면에 보여줄 값"과 "약물노트 그룹 조회에 넘길 값"이 다르다.
// 표시용은 빈 값을 채워 넣지만(병원 정보 없음 / 시작일), 그 값을 조회 키로 넘기면
// findByGroup이 hospital_name = '병원 정보 없음' 같은 비교를 하게 되어 결과가 0건이 된다.
// 그래서 group* 필드에 원본을 그대로(null 포함) 실어 보내고, null인 필드는 쿼리 파라미터에서 생략하면 된다.
@Schema(description = "복약 부족 카드")
public record MedicationDepletionResponse(

        @Schema(description = "약 ID") Long medicationId,
        @Schema(description = "약 이름") String drugName,
        @Schema(description = "약 별명") String drugNickname,
        @Schema(description = "남은 복용 일수 (오늘=0, 이미 지났으면 음수)") long remainingDays,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "복용 종료일") LocalDate endDate,

        @Schema(description = "화면 표시용 병원명 (없으면 \"병원 정보 없음\")") String hospitalName,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "화면 표시용 처방일 (없으면 복용 시작일)") LocalDate prescriptionDate,

        @Schema(description = "약물노트 그룹 조회용 병원명 원본 (null이면 파라미터 생략)") String groupHospitalName,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @Schema(description = "약물노트 그룹 조회용 처방일 원본 (null이면 파라미터 생략)") LocalDate groupPrescriptionDate
) {
}
