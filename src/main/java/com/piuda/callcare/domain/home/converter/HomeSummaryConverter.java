package com.piuda.callcare.domain.home.converter;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.home.dto.response.HomeSummaryResponse;
import com.piuda.callcare.domain.home.dto.response.NextDoseResponse;
import com.piuda.callcare.domain.home.enums.HomeCardMode;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.entity.MedicationSchedule;

@Component
public class HomeSummaryConverter {

    // 집계값 → HomeSummaryResponse (모드별 null 여부는 서비스에서 정해 넘긴다)
    public HomeSummaryResponse toResponse(LocalDate date, HomeCardMode mode,
                                          Integer scheduledCount, Integer completedCount, NextDoseResponse nextDose) {
        return new HomeSummaryResponse(date, mode, scheduledCount, completedCount, nextDose);
    }

    // MedicationSchedule → NextDoseResponse (같은 시간대의 나머지 약 수는 서비스에서 세어 주입)
    public NextDoseResponse toNextDose(MedicationSchedule first, int otherCount) {
        Medication medication = first.getMedication();
        return new NextDoseResponse(
                first.getMealTime(),
                first.getMealTime().getDescription(),
                medication.getDrugName(),
                medication.getDrugNickname(),
                otherCount
        );
    }
}
