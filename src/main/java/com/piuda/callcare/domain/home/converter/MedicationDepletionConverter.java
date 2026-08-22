package com.piuda.callcare.domain.home.converter;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.home.constant.HomeDisplayNames;
import com.piuda.callcare.domain.home.dto.response.MedicationDepletionResponse;
import com.piuda.callcare.domain.medication.entity.Medication;

@Component
public class MedicationDepletionConverter {

    // Medication → MedicationDepletionResponse (남은 일수는 서비스에서 계산해 주입)
    // 병원명·처방일은 표시용(빈 값 대체)과 딥링크용(원본 그대로)을 나란히 담는다 — 응답 DTO 주석 참고.
    public MedicationDepletionResponse toResponse(Medication medication, long remainingDays) {
        return new MedicationDepletionResponse(
                medication.getId(),
                medication.getDrugName(),
                medication.getDrugNickname(),
                remainingDays,
                medication.getEndDate(),
                displayHospitalName(medication),
                displayPrescriptionDate(medication),
                medication.getHospitalName(),
                medication.getPrescriptionDate()
        );
    }

    // 홈 카드와 같은 규칙 — 병원명이 없으면 "병원 정보 없음"
    private String displayHospitalName(Medication medication) {
        return (medication.getHospitalName() != null)
                ? medication.getHospitalName()
                : HomeDisplayNames.NO_HOSPITAL_NAME;
    }

    // 약물노트 목록과 같은 규칙 — 처방일이 없으면 복용 시작일로 대신 보여준다
    private LocalDate displayPrescriptionDate(Medication medication) {
        return (medication.getPrescriptionDate() != null)
                ? medication.getPrescriptionDate()
                : medication.getStartDate();
    }
}
