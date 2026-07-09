package com.piuda.callcare.domain.medication.service.query;

import com.piuda.callcare.domain.medication.converter.MedicationConverter;
import com.piuda.callcare.domain.medication.dto.response.MedicationGroupItemResponse;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicationQueryService {

    private final MedicationRepository medicationRepository;
    private final SeniorRepository seniorRepository;
    private final MedicationConverter medicationConverter;

    public List<MedicationGroupItemResponse> getGroup(Long userId, Long seniorId, String hospitalName, LocalDate prescriptionDate) {
        if (userId != null) {
            seniorRepository.findByIdAndUser_Id(seniorId, userId)
                    .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));
        }
        List<Medication> medications = medicationRepository.findByGroup(seniorId, hospitalName, prescriptionDate);
        return medications.stream()
                .map(medicationConverter::toGroupItemResponse)
                .toList();
    }
}
