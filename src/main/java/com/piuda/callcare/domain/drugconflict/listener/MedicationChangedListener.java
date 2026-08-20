package com.piuda.callcare.domain.drugconflict.listener;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.drugconflict.service.command.DrugConflictCommandService;
import com.piuda.callcare.domain.medication.event.MedicationChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 약 구성이 바뀌면 충돌을 다시 분석한다.
 * <p>
 * <b>AFTER_COMMIT인 이유</b>는 약 등록/수정이 롤백됐는데 그 약을 전제로 분석이 돌면 안 되기 때문이다.
 * 새 트랜잭션이 필요한 이유(완료된 트랜잭션에 올라타면 쓰기가 커밋되지 않는다)와 그 경계를
 * 리스너가 아니라 서비스에 둔 이유는 {@code analyzeInNewTransaction} 주석에 적어 뒀다.
 * <p>
 * 재분석 실패가 약 등록/수정을 되돌리지 않도록 예외를 여기서 가둔다 — 원본은 이미 커밋됐고,
 * 분석은 리포트 진입 시에도 다시 도는 보조 경로다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationChangedListener {

    private final DrugConflictCommandService drugConflictCommandService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reanalyzeConflicts(MedicationChangedEvent event) {
        try {
            drugConflictCommandService.analyzeInNewTransaction(event.seniorId());
        } catch (Exception e) {
            log.error("약 변경 후 충돌 재분석 실패 - seniorId={}", event.seniorId(), e);
        }
    }
}
