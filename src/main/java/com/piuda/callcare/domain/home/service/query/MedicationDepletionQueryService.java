package com.piuda.callcare.domain.home.service.query;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.home.converter.MedicationDepletionConverter;
import com.piuda.callcare.domain.home.dto.response.MedicationDepletionResponse;
import com.piuda.callcare.domain.home.service.DepletionCalculator;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicationDepletionQueryService {

    private final SeniorRepository seniorRepository;
    private final MedicationRepository medicationRepository;
    private final MedicationDepletionConverter depletionConverter;
    private final DepletionCalculator depletionCalculator;

    // 어르신의 활성 약 중 소진 임박(남은 일수 <= 임계값)인 약만 추려 남은 일수 오름차순(가장 급한 약이 위)으로 반환
    public List<MedicationDepletionResponse> getDepletingMedications(Long seniorId) {
        // TODO: 인증 도입 후 seniorId 소유권 검증 추가
        if (!seniorRepository.existsById(seniorId)) {
            throw new CallCareException(ErrorCode.SENIOR_NOT_FOUND);
        }

        LocalDate today = LocalDate.now();

        return medicationRepository.findActiveMedicationsForDepletion(seniorId).stream()
                .filter(m -> depletionCalculator.isDepleting(m.getEndDate(), today))
                .sorted(Comparator.comparingLong((Medication m) -> depletionCalculator.remainingDays(m.getEndDate(), today)))
                .map(m -> depletionConverter.toResponse(m, depletionCalculator.remainingDays(m.getEndDate(), today)))
                .toList();
    }
}