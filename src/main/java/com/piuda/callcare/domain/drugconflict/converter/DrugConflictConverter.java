package com.piuda.callcare.domain.drugconflict.converter;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.drugconflict.dto.response.ConflictDrug;
import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictDetailResponse;
import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictResponse;
import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.medication.entity.Medication;

@Component
public class DrugConflictConverter {

    // DrugConflict → DrugConflictResponse (목록 카드용)
    // 카드 표시값(제품종류·제품명·처방기관·처방날짜)은 저장하지 않고 Medication에서 조합한다.
    public DrugConflictResponse toResponse(DrugConflict conflict) {
        return new DrugConflictResponse(
                conflict.getId(),
                toConflictDrug(conflict.getMedication1()),
                toConflictDrug(conflict.getMedication2()),
                conflict.getSeverity(),
                conflict.getSeverity().getLabel(),
                conflict.getIsResolved()
        );
    }

    // DrugConflict → DrugConflictDetailResponse (상세 조회용)
    // 약 정보는 목록과 동일한 ConflictDrug로 채워 두 응답의 형태를 맞춘다.
    public DrugConflictDetailResponse toDetail(DrugConflict conflict) {
        return new DrugConflictDetailResponse(
                conflict.getId(),
                toConflictDrug(conflict.getMedication1()),
                toConflictDrug(conflict.getMedication2()),
                conflict.getSeverity(),
                conflict.getSeverity().getLabel(),
                conflict.getConflictDescription(),
                conflict.getIsResolved(),
                conflict.getCreatedAt()
        );
    }

    // Medication → ConflictDrug (목록·상세에 표시할 약 정보 합성)
    private ConflictDrug toConflictDrug(Medication medication) {
        return new ConflictDrug(
                medication.getId(),
                medication.getDrugType(),
                medication.getDrugName(),
                medication.getDrugNickname(),
                medication.getHospitalName(),
                resolvePrescriptionDate(medication)
        );
    }

    // 처방 날짜 폴백: prescription_date(OCR) → 없으면 start_date(사용자 입력)
    private LocalDate resolvePrescriptionDate(Medication medication) {
        return medication.getPrescriptionDate() != null
                ? medication.getPrescriptionDate()
                : medication.getStartDate();
    }
}
