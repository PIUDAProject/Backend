package com.piuda.callcare.domain.home.converter;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.home.dto.response.MedicationDepletionResponse;
import com.piuda.callcare.domain.medication.entity.Medication;

@Component
public class MedicationDepletionConverter {

    // Medication → MedicationDepletionResponse (남은 일수는 서비스에서 계산해 주입)
    public MedicationDepletionResponse toResponse(Medication medication, long remainingDays) {
        return new MedicationDepletionResponse(
                medication.getId(),
                medication.getDrugName(),
                medication.getDrugNickname(),
                remainingDays,
                medication.getEndDate()
        );
    }
}