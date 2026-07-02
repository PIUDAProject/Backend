package com.piuda.callcare.domain.drugconflict.service.command;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.drugconflict.repository.DrugConflictRepository;
import com.piuda.callcare.domain.drugconflict.service.DrugConflictMatcher;
import com.piuda.callcare.domain.drugconflict.service.DrugConflictMatcher.ConflictMatch;
import com.piuda.callcare.domain.medication.entity.Medication;
import com.piuda.callcare.domain.medication.repository.MedicationRepository;
import com.piuda.callcare.domain.senior.entity.Senior;
import com.piuda.callcare.domain.senior.repository.SeniorRepository;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class DrugConflictCommandService {

    private final SeniorRepository seniorRepository;
    private final MedicationRepository medicationRepository;
    private final DrugConflictRepository drugConflictRepository;
    private final DrugConflictMatcher drugConflictMatcher;

    // 리포트 진입 트리거: 어르신의 활성 약(DrugInfo 연결) 전체 쌍을 검사해 새 충돌만 저장.
    // 실시간 재계산이 아니라 분석 결과를 DrugConflict에 적재(4단계 = 저장값).
    public void analyze(Long seniorId) {
        Senior senior = seniorRepository.findById(seniorId)
                .orElseThrow(() -> new CallCareException(ErrorCode.SENIOR_NOT_FOUND));

        // TODO: 인증 도입 후 seniorId 소유권 검증 추가

        List<Medication> medications = medicationRepository.findActiveWithDrugInfoBySeniorId(seniorId);

        for (int i = 0; i < medications.size(); i++) {
            for (int j = i + 1; j < medications.size(); j++) {
                Medication a = medications.get(i);
                Medication b = medications.get(j);
                drugConflictMatcher.match(a.getDrugInfo(), b.getDrugInfo())
                        .ifPresent(match -> saveIfAbsent(senior, a, b, match));
            }
        }
    }

    // (medication_id_1 < medication_id_2)로 정규화해 순서 무관 중복 저장을 방지(find→분기, 2단계 upsert 패턴).
    private void saveIfAbsent(Senior senior, Medication x, Medication y, ConflictMatch match) {
        Medication first = x.getId() < y.getId() ? x : y;
        Medication second = x.getId() < y.getId() ? y : x;

        boolean exists = drugConflictRepository.existsBySenior_IdAndMedication1_IdAndMedication2_Id(
                senior.getId(), first.getId(), second.getId());
        if (exists) {
            return;
        }

        drugConflictRepository.save(DrugConflict.builder()
                .senior(senior)
                .medication1(first)
                .medication2(second)
                .severity(match.severity())
                .conflictDescription(match.description())
                .build());
    }
}