package com.piuda.callcare.domain.medication.service.query;

import com.piuda.callcare.domain.medication.converter.MedicationConverter;
import com.piuda.callcare.domain.medication.dto.response.MedicationDetailResponse;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicationQueryService {

    private final MedicationRepository medicationRepository;
    private final MedicationConverter medicationConverter;

    public MedicationDetailResponse getDetail(Long userId, Long medicationId) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new CallCareException(ErrorCode.MEDICATION_NOT_FOUND));
        if (userId != null && !medication.getSenior().getUser().getId().equals(userId)) {
            throw new CallCareException(ErrorCode.FORBIDDEN);
        }
        return medicationConverter.toDetailResponse(medication);
    }
}
