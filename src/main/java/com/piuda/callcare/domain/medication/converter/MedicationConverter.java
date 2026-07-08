package com.piuda.callcare.domain.medication.converter;

import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.medication.dto.response.MedicationDetailResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationResponse;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MedicationConverter {

    // Medication → MedicationDetailResponse (약 상세 조회용, DrugInfo 없으면 전 필드 null)
    public MedicationDetailResponse toDetailResponse(Medication medication) {
        DrugInfo drugInfo = medication.getDrugInfo();
        if (drugInfo == null) {
            return new MedicationDetailResponse(medication.getDrugName(), null, null, null, null);
        }
        return new MedicationDetailResponse(
                medication.getDrugName(),
                drugInfo.getEfcyQesitm(),
                drugInfo.getUseMethodQesitm(),
                drugInfo.getAtpnQesitm(),
                drugInfo.getSeQesitm()
        );
    }

    // Medication + 스케줄 목록 → MedicationResponse
    public MedicationResponse toResponse(Medication medication, List<MedicationSchedule> schedules) {
        List<MealTime> mealTimes = schedules.stream()
                .map(MedicationSchedule::getMealTime)
                .toList();

        return new MedicationResponse(
                medication.getId(),
                medication.getHospitalName(),
                medication.getDrugName(),
                medication.getDosagePerTime(),
                medication.getTimesPerDay(),
                medication.getTotalDays(),
                medication.getStartDate(),
                medication.getEndDate(),
                mealTimes
        );
    }
}
