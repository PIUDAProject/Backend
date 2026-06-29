package com.piuda.callcare.domain.home.converter;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.home.dto.response.MedicationCardResponse;
import com.piuda.callcare.domain.home.enums.CompletedStatus;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;

@Component
public class HomeCardConverter {

    // MedicationSchedule → MedicationCardResponse (완료 상태는 서비스에서 합성해 주입)
    public MedicationCardResponse toCard(MedicationSchedule schedule, boolean isTaken, CompletedStatus completedStatus) {
        Medication m = schedule.getMedication();
        return new MedicationCardResponse(
                m.getId(),
                m.getDrugName(),
                m.getDrugNickname(),
                m.getDrugType(),
                m.getImageUrl(),
                m.getDosagePerTime(),
                m.getTimesPerDay(),
                isTaken,
                completedStatus
        );
    }
}
