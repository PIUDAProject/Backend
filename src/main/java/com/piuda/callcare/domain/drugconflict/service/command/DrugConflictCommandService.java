package com.piuda.callcare.domain.drugconflict.service.command;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.drugconflict.entity.DrugConflict;
import com.piuda.callcare.domain.drugconflict.event.DrugConflictDetectedEvent;
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
    private final ApplicationEventPublisher eventPublisher;

    // 약 변경 이벤트(AFTER_COMMIT) 트리거: 이미 완료된 원본 트랜잭션에 올라타면 쓰기가 커밋되지 않으므로
    // 새 트랜잭션에서 분석한다. 트랜잭션 경계를 리스너가 아니라 여기에 두는 이유는, 리스너에 REQUIRES_NEW를
    // 걸면 분석 실패가 커밋 단계의 UnexpectedRollbackException으로 바뀌어 리스너의 catch를 빠져나가기
    // 때문이다 — 경계가 이 메서드에 있으면 원래 예외가 그대로 호출자에게 전달돼 리스너에서 가둘 수 있다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void analyzeInNewTransaction(Long seniorId) {
        analyze(seniorId);
    }

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
                            // 알릴 만한 결과(신규·등급 상승)일 때만 이벤트가 나온다.
                            // 발송은 이 트랜잭션이 커밋된 뒤에 일어난다(AFTER_COMMIT 리스너) —
                            // 푸시는 회수할 수 없으므로 롤백 가능한 구간에서 내보내면 안 된다.
                            saveOrUpdate(senior, a, b, match).ifPresent(eventPublisher::publishEvent);
                            matchedPairs.add(MedicationPair.of(a, b));
                        });
            }
        }

        deleteStaleConflicts(seniorId, medications, matchedPairs);
    }

    // 보호자가 충돌을 "확인함"으로 처리한다 — 확인한 조합은 목록에서 빠진다(상세는 계속 열린다).
    // 목록과 같은 노출 조건으로 찾는다: 목록에 뜨지도 않는 충돌을 확인 처리할 일은 없다.
    // 여기에 소유자(userId) 조건을 더해 남의 충돌은 아예 조회되지 않게 한다 — 경고를 화면에서
    // 지우는 상태 변경이라 조회 API들처럼 소유권 검증을 미뤄 둘 수 없다.
    // 이미 확인한 충돌을 다시 확인해도 결과가 같아 멱등하다.
    public void resolve(Long userId, Long conflictId) {
        DrugConflict conflict = drugConflictRepository.findWithMedicationsByIdAndUserId(conflictId, userId)
                .orElseThrow(() -> new CallCareException(ErrorCode.DRUG_CONFLICT_NOT_FOUND));

        conflict.resolve(); // dirty checking
    }

    // 이번 분석에서 다시 매칭되지 않은 기존 충돌을 제거 — 약 정보가 바뀌어 더 이상 충돌이 아닌 쌍이
    // 옛 등급으로 목록에 남는 것을 막는다(upsert만으로는 사라진 충돌을 정리할 수 없다).
    // 삭제 범위는 이번 분석 대상(활성·미삭제) 약들로만 이뤄진 쌍에 한정한다 —
    // 비활성·삭제된 약이 낀 행은 애초에 매칭 대상이 아니었을 뿐이므로 지우면 분석 이력이 사라진다.
    //
    // 이 한정이 확인 상태(is_resolved)를 지키는 장치이기도 하다. 약을 잠시 비활성화했다 되돌려도 그 약이 낀
    // 행은 여기서 지워지지 않아 재활성 시 같은 행이 그대로 재사용된다(확인 상태·createdAt 보존, 등급이
    // 그대로면 재알림도 없다). 그래서 남는 삭제 경로는 "두 약 모두 활성인데 매칭이 사라진 경우"뿐이고,
    // 그때는 충돌이 실제로 소멸한 것이라 행을 지우는 것이 맞다.
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
    //
    // 알림 이벤트를 돌려주는 경우는 신규 저장과 등급 상승 두 가지뿐이다. 리포트를 열 때마다 이 분석이
    // 다시 도는 구조라, 이 구분이 없으면 조회할 때마다 같은 조합의 푸시가 반복해서 나간다.
    private Optional<DrugConflictDetectedEvent> saveOrUpdate(Senior senior, Medication x, Medication y, ConflictMatch match) {
        Medication first = x.getId() < y.getId() ? x : y;
        Medication second = x.getId() < y.getId() ? y : x;

        Optional<DrugConflict> existing = drugConflictRepository.findBySenior_IdAndMedication1_IdAndMedication2_Id(
                senior.getId(), first.getId(), second.getId());
        if (existing.isPresent()) {
            // 이미 커밋된 쌍 → 최신 결과로 갱신(dirty checking). 값이 같으면 updateAnalysis가 UPDATE를 생략한다.
            DrugConflict conflict = existing.get();
            boolean escalated = conflict.updateAnalysis(match.severity(), match.description());
            return escalated
                    ? Optional.of(new DrugConflictDetectedEvent(conflict.getId(), true))
                    : Optional.empty();
        }

        // 동시 요청 레이스는 UNIQUE 제약(uk_drug_conflict_senior_med1_med2)이 막는다.
        // 제약 위반 시 예외를 잡지 않고 전파한다 — 잡아도 세션이 rollback-only라 요청은 어차피 실패하고,
        // analyze()는 멱등하므로 재요청하면 위 existing 경로로 정상 처리된다.
        DrugConflict saved = drugConflictRepository.save(DrugConflict.builder()
                .senior(senior)
                .medication1(first)
                .medication2(second)
                .severity(match.severity())
                .conflictDescription(match.description())
                .build());
        return Optional.of(new DrugConflictDetectedEvent(saved.getId(), false));
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
