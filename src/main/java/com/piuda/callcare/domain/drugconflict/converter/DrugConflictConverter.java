package com.piuda.callcare.domain.drugconflict.converter;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictDetailResponse;
import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictResponse;
import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;

@Component
public class DrugConflictConverter {

    // DrugConflict → DrugConflictResponse (목록 카드용)
    public DrugConflictResponse toResponse(DrugConflict conflict) {
        return new DrugConflictResponse(
                conflict.getId(),
                conflict.getMedication1().getId(),
                conflict.getMedication1().getDrugName(),
                conflict.getMedication2().getId(),
                conflict.getMedication2().getDrugName(),
                conflict.getSeverity(),
                conflict.getSeverity().getLabel(),
                conflict.getIsResolved()
        );
    }

    // DrugConflict → DrugConflictDetailResponse (상세 조회용)
    public DrugConflictDetailResponse toDetail(DrugConflict conflict) {
        return new DrugConflictDetailResponse(
                conflict.getId(),
                conflict.getMedication1().getId(),
                conflict.getMedication1().getDrugName(),
                conflict.getMedication1().getDrugNickname(),
                conflict.getMedication2().getId(),
                conflict.getMedication2().getDrugName(),
                conflict.getMedication2().getDrugNickname(),
                conflict.getSeverity(),
                conflict.getSeverity().getLabel(),
                conflict.getConflictDescription(),
                conflict.getIsResolved(),
                conflict.getCreatedAt()
        );
    }
}