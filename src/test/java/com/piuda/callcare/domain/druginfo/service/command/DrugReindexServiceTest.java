package com.piuda.callcare.domain.druginfo.service.command;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.entity.DrugInfo;
import com.piuda.callcare.domain.druginfo.entity.DrugSyncHistory;
import com.piuda.callcare.domain.druginfo.enums.DrugSyncStatus;
import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import com.piuda.callcare.domain.druginfo.repository.DrugSyncHistoryRepository;
import com.piuda.callcare.domain.druginfo.service.DrugIndexManager;
import com.piuda.callcare.global.config.redis.IdempotencyKeyStore;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.doAnswer;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("DrugReindexService 단위 테스트 — 무중단 재색인")
class DrugReindexServiceTest {

    @Mock private DrugInfoRepository drugInfoRepository;
    @Mock private DrugInfoConverter drugInfoConverter;
    @Mock private DrugSyncHistoryRepository drugSyncHistoryRepository;
    @Mock private DrugIndexManager drugIndexManager;
    @Mock private IdempotencyKeyStore idempotencyKeyStore;
    @Mock private TaskExecutor taskExecutor;

    private DrugReindexService drugReindexService;

    @BeforeEach
    void setUp() {
        drugReindexService = new DrugReindexService(
                drugInfoRepository, drugInfoConverter, drugSyncHistoryRepository,
                drugIndexManager, idempotencyKeyStore, taskExecutor);
    }

    // taskExecutor.execute(runnable) 를 인라인 실행으로 스텁 — executeReindex 경로까지 검증
    private void runTaskInline() {
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(taskExecutor).execute(any());
    }

    @Test
    @DisplayName("예외 케이스: 이미 재색인이 실행 중이면 DRUG_REINDEX_ALREADY_RUNNING")
    void startReindex_throws_when_lock_taken() {
        given(idempotencyKeyStore.tryAcquire(eq("drug:reindex"), any(Duration.class))).willReturn(false);

        assertThatThrownBy(() -> drugReindexService.startReindex())
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRUG_REINDEX_ALREADY_RUNNING);

        then(drugSyncHistoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("정상 케이스: 새 인덱스 생성 → 색인 → alias 스왑 → 구 인덱스 정리, 이력 SUCCESS")
    void startReindex_success_flow() {
        DrugSyncHistory history = DrugSyncHistory.start();
        given(idempotencyKeyStore.tryAcquire(eq("drug:reindex"), any(Duration.class))).willReturn(true);
        given(drugSyncHistoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(drugSyncHistoryRepository.findById(any())).willReturn(Optional.of(history));
        given(drugIndexManager.resolveAliasTargets()).willReturn(Set.of("drug_info-old"));
        given(drugIndexManager.createTimestampedIndex()).willReturn("drug_info-new");
        DrugInfo drug = DrugInfo.builder().itemSeq("1").itemName("타이레놀정500mg").build();
        given(drugInfoRepository.findAll()).willReturn(List.of(drug));
        given(drugInfoConverter.toDocument(drug)).willReturn(null);

        runTaskInline();

        DrugReindexService.ReindexStartResult result = drugReindexService.startReindex();

        assertThat(result.status()).isEqualTo(DrugSyncStatus.RUNNING);
        then(drugIndexManager).should().createTimestampedIndex();
        then(drugIndexManager).should().bulkIndex(any(), eq("drug_info-new"));
        then(drugIndexManager).should().switchAlias(eq("drug_info-new"), eq(Set.of("drug_info-old")));
        then(drugIndexManager).should().deleteObsoleteIndices();
        then(idempotencyKeyStore).should().release("drug:reindex");
        assertThat(history.getStatus()).isEqualTo(DrugSyncStatus.SUCCESS);
    }

    @Test
    @DisplayName("실패 케이스: 이력 저장이 실패하면 락을 해제하고 예외를 전파한다")
    void startReindex_releases_lock_when_history_save_fails() {
        given(idempotencyKeyStore.tryAcquire(eq("drug:reindex"), any(Duration.class))).willReturn(true);
        given(drugSyncHistoryRepository.save(any())).willThrow(new RuntimeException("DB 연결 실패"));

        assertThatThrownBy(() -> drugReindexService.startReindex())
                .isInstanceOf(RuntimeException.class);

        then(idempotencyKeyStore).should().release("drug:reindex");
    }

    @Test
    @DisplayName("실패 케이스: 비동기 작업 제출 실패 시 이력 FAILED, 락 해제, DRUG_REINDEX_FAILED")
    void startReindex_task_rejected() {
        DrugSyncHistory history = DrugSyncHistory.start();
        given(idempotencyKeyStore.tryAcquire(eq("drug:reindex"), any(Duration.class))).willReturn(true);
        given(drugSyncHistoryRepository.save(any())).willReturn(history);
        doThrow(new org.springframework.core.task.TaskRejectedException("pool full"))
                .when(taskExecutor).execute(any());

        assertThatThrownBy(() -> drugReindexService.startReindex())
                .isInstanceOf(CallCareException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRUG_REINDEX_FAILED);

        assertThat(history.getStatus()).isEqualTo(DrugSyncStatus.FAILED);
        then(idempotencyKeyStore).should().release("drug:reindex");
    }

    @Test
    @DisplayName("정상 케이스: alias 스왑 후 구 인덱스 정리가 실패해도 재색인은 SUCCESS")
    void reindex_success_even_when_cleanup_fails() {
        DrugSyncHistory history = DrugSyncHistory.start();
        given(idempotencyKeyStore.tryAcquire(eq("drug:reindex"), any(Duration.class))).willReturn(true);
        given(drugSyncHistoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(drugSyncHistoryRepository.findById(any())).willReturn(Optional.of(history));
        given(drugIndexManager.resolveAliasTargets()).willReturn(Set.of());
        given(drugIndexManager.createTimestampedIndex()).willReturn("drug_info-new");
        given(drugInfoRepository.findAll()).willReturn(List.of());
        doThrow(new RuntimeException("인덱스 목록 조회 실패"))
                .when(drugIndexManager).deleteObsoleteIndices();

        runTaskInline();
        drugReindexService.startReindex();

        assertThat(history.getStatus()).isEqualTo(DrugSyncStatus.SUCCESS);
        then(idempotencyKeyStore).should().release("drug:reindex");
    }

    @Test
    @DisplayName("실패 케이스: alias 스왑 실패 시 이력 FAILED, 새 인덱스 정리, 락 해제")
    void executeReindex_marks_failed_and_releases_lock() {
        DrugSyncHistory history = DrugSyncHistory.start();
        given(idempotencyKeyStore.tryAcquire(eq("drug:reindex"), any(Duration.class))).willReturn(true);
        given(drugSyncHistoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(drugSyncHistoryRepository.findById(any())).willReturn(Optional.of(history));
        given(drugIndexManager.resolveAliasTargets()).willReturn(Set.of(), Set.of()); // 스왑 전/정리 판단 모두 미포함
        given(drugIndexManager.createTimestampedIndex()).willReturn("drug_info-new");
        given(drugInfoRepository.findAll()).willReturn(List.of());
        doAnswer(inv -> { throw new RuntimeException("alias swap 실패"); })
                .when(drugIndexManager).switchAlias(anyString(), any());

        runTaskInline();

        drugReindexService.startReindex();

        assertThat(history.getStatus()).isEqualTo(DrugSyncStatus.FAILED);
        then(drugIndexManager).should().deleteIndex("drug_info-new");
        then(idempotencyKeyStore).should().release("drug:reindex");
    }
}
