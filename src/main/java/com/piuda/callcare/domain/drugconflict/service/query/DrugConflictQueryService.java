package com.piuda.callcare.domain.drugconflict.service.query;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.drugconflict.converter.DrugConflictConverter;
import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictDetailResponse;
import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictResponse;
import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.drugconflict.repository.DrugConflictRepository;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DrugConflictQueryService {

    private final SeniorRepository seniorRepository;
    private final DrugConflictRepository drugConflictRepository;
    private final DrugConflictConverter drugConflictConverter;

    // 어르신의 저장된 충돌 목록. 금기(심각도 높은 순) 먼저, 충돌 없으면 빈 목록(프론트가 안내 메시지 처리).
    public List<DrugConflictResponse> getConflicts(Long seniorId) {
        if (!seniorRepository.existsById(seniorId)) {
            throw new CallCareException(ErrorCode.SENIOR_NOT_FOUND);
        }

        // TODO: 인증 도입 후 seniorId 소유권 검증 추가

        return drugConflictRepository.findAllWithMedicationsBySeniorId(seniorId).stream()
                .sorted(Comparator.comparingInt((DrugConflict c) -> c.getSeverity().getPriority()).reversed())
                .map(drugConflictConverter::toResponse)
                .toList();
    }

    // 충돌 상세(카드 클릭 시)
    public DrugConflictDetailResponse getConflictDetail(Long conflictId) {
        DrugConflict conflict = drugConflictRepository.findWithMedicationsById(conflictId)
                .orElseThrow(() -> new CallCareException(ErrorCode.DRUG_CONFLICT_NOT_FOUND));
        return drugConflictConverter.toDetail(conflict);
    }
}