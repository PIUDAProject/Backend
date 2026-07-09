package com.piuda.callcare.domain.medication.converter;

import com.piuda.callcare.domain.medication.dto.response.MedicationGroupItemResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationResponse;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;
import com.piuda.callcare.domain.medication.enums.MealTime;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MedicationConverter {

    // Medication → MedicationGroupItemResponse (약물노트 그룹 상세 조회용)
    public MedicationGroupItemResponse toGroupItemResponse(Medication medication) {
        return new MedicationGroupItemResponse(
                medication.getId(),
                medication.getDrugName(),
                medication.getDrugType(),
                medication.getImageUrl(),
                medication.getTimesPerDay(),
                medication.getDosagePerTime(),
                medication.getStartDate(),
                medication.getEndDate(),
                medication.getTotalDays(),
                medication.getMemo()
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
