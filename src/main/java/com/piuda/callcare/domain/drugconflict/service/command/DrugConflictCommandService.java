package com.piuda.callcare.domain.drugconflict.service.command;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
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
        Set<MedicationPair> matchedPairs = new HashSet<>();

        for (int i = 0; i < medications.size(); i++) {
            for (int j = i + 1; j < medications.size(); j++) {
                Medication a = medications.get(i);
                Medication b = medications.get(j);
                drugConflictMatcher.match(a.getDrugInfo(), b.getDrugInfo())
                        .ifPresent(match -> {
                            saveOrUpdate(senior, a, b, match);
                            matchedPairs.add(MedicationPair.of(a, b));
                        });
            }
        }

        deleteStaleConflicts(seniorId, medications, matchedPairs);
    }

    // 이번 분석에서 다시 매칭되지 않은 기존 충돌을 제거 — 약 정보가 바뀌어 더 이상 충돌이 아닌 쌍이
    // 옛 등급으로 목록에 남는 것을 막는다(upsert만으로는 사라진 충돌을 정리할 수 없다).
    // 삭제 범위는 이번 분석 대상(활성·미삭제) 약들로만 이뤄진 쌍에 한정한다 —
    // 비활성·삭제된 약이 낀 행은 애초에 매칭 대상이 아니었을 뿐이므로 지우면 분석 이력이 사라진다.
    private void deleteStaleConflicts(Long seniorId, List<Medication> analyzed, Set<MedicationPair> matchedPairs) {
        Set<Long> analyzedIds = analyzed.stream().map(Medication::getId).collect(Collectors.toSet());

        List<DrugConflict> stale = drugConflictRepository.findAllWithMedicationsForReanalysis(seniorId).stream()
                .filter(conflict -> analyzedIds.contains(conflict.getMedication1().getId())
                        && analyzedIds.contains(conflict.getMedication2().getId()))
                .filter(conflict -> !matchedPairs.contains(MedicationPair.of(
                        conflict.getMedication1(), conflict.getMedication2())))
                .toList();

        if (!stale.isEmpty()) {
            drugConflictRepository.deleteAll(stale);
        }
    }

    // (medication_id_1 < medication_id_2)로 정규화해 순서 무관 중복을 방지하고,
    // 기존 쌍이 있으면 최신 분석 결과로 upsert(값이 실제로 바뀐 경우만 UPDATE). 없으면 신규 저장.
    private void saveOrUpdate(Senior senior, Medication x, Medication y, ConflictMatch match) {
        Medication first = x.getId() < y.getId() ? x : y;
        Medication second = x.getId() < y.getId() ? y : x;

        Optional<DrugConflict> existing = drugConflictRepository.findBySenior_IdAndMedication1_IdAndMedication2_Id(
                senior.getId(), first.getId(), second.getId());
        if (existing.isPresent()) {
            // 이미 커밋된 쌍 → 최신 결과로 갱신(dirty checking). 값이 같으면 updateAnalysis가 UPDATE를 생략한다.
            existing.get().updateAnalysis(match.severity(), match.description());
            return;
        }

        try {
            drugConflictRepository.save(DrugConflict.builder()
                    .senior(senior)
                    .medication1(first)
                    .medication2(second)
                    .severity(match.severity())
                    .conflictDescription(match.description())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 같은 조합이 먼저 INSERT된 레이스(uk_drug_conflict_senior_med1_med2).
            // 정합성은 UNIQUE 제약이 보장하므로 이번 요청은 저장을 건너뛴다(삼중 방어 유지).
        }
    }

    // 순서 무관 비교용 약 쌍 키. 저장 정규화 규칙과 동일하게 (작은 id, 큰 id)로 맞춘다.
    private record MedicationPair(Long first, Long second) {

        static MedicationPair of(Medication x, Medication y) {
            return x.getId() < y.getId()
                    ? new MedicationPair(x.getId(), y.getId())
                    : new MedicationPair(y.getId(), x.getId());
        }
    }
}
