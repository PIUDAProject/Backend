package com.piuda.callcare.domain.druginfo.service.command;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.document.DrugDocument;
import com.piuda.callcare.domain.druginfo.entity.DrugSyncHistory;
import com.piuda.callcare.domain.druginfo.enums.DrugSyncStatus;
import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import com.piuda.callcare.domain.druginfo.repository.DrugSyncHistoryRepository;
import com.piuda.callcare.domain.druginfo.service.DrugIndexManager;
import com.piuda.callcare.global.config.redis.IdempotencyKeyStore;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 약품 검색 인덱스(MySQL drug_info → Elasticsearch)를 무중단으로 재색인한다.
 * <p>
 * 새 물리 인덱스에 전체 색인 → alias 원자 스왑 → 구 인덱스 정리 순으로 진행하며,
 * 재색인 도중에도 사용자는 기존 alias로 검색을 계속할 수 있다.
 * <p>
 * 동시 실행은 Redis 락({@link IdempotencyKeyStore})으로 차단한다. TTL이 붙어 있어
 * 프로세스가 재색인 도중 종료돼도 락이 자동 만료된다.
 */
@Slf4j
@Service
public class DrugReindexService {

    private static final String REINDEX_LOCK_KEY = "drug:reindex";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final DrugInfoRepository drugInfoRepository;
    private final DrugInfoConverter drugInfoConverter;
    private final DrugSyncHistoryRepository drugSyncHistoryRepository;
    private final DrugIndexManager drugIndexManager;
    private final IdempotencyKeyStore idempotencyKeyStore;
    private final TaskExecutor taskExecutor;

    public DrugReindexService(
            DrugInfoRepository drugInfoRepository,
            DrugInfoConverter drugInfoConverter,
            DrugSyncHistoryRepository drugSyncHistoryRepository,
            DrugIndexManager drugIndexManager,
            IdempotencyKeyStore idempotencyKeyStore,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.drugInfoRepository = drugInfoRepository;
        this.drugInfoConverter = drugInfoConverter;
        this.drugSyncHistoryRepository = drugSyncHistoryRepository;
        this.drugIndexManager = drugIndexManager;
        this.idempotencyKeyStore = idempotencyKeyStore;
        this.taskExecutor = taskExecutor;
    }

    public record ReindexStartResult(Long historyId, DrugSyncStatus status) {
    }

    // 재색인을 백그라운드에서 시작하고 이력 ID를 즉시 반환한다. 이미 실행 중이면 409.
    public ReindexStartResult startReindex() {
        if (!idempotencyKeyStore.tryAcquire(REINDEX_LOCK_KEY, LOCK_TTL)) {
            log.warn("이미 진행 중인 약품 재색인이 있어 요청을 거부합니다.");
            throw new CallCareException(ErrorCode.DRUG_REINDEX_ALREADY_RUNNING);
        }

        DrugSyncHistory history = drugSyncHistoryRepository.save(DrugSyncHistory.start());
        log.info("약품 ES 재색인 요청 접수 (historyId: {})", history.getId());

        try {
            taskExecutor.execute(() -> executeReindex(history.getId()));
        } catch (TaskRejectedException e) {
            history.fail("재색인 비동기 작업 제출에 실패했습니다.");
            drugSyncHistoryRepository.save(history);
            idempotencyKeyStore.release(REINDEX_LOCK_KEY);
            throw new CallCareException(ErrorCode.DRUG_REINDEX_FAILED, "재색인 작업을 시작할 수 없습니다.");
        }

        return new ReindexStartResult(history.getId(), DrugSyncStatus.RUNNING);
    }

    private void executeReindex(Long historyId) {
        DrugSyncHistory history = drugSyncHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalStateException("약품 재색인 이력을 찾을 수 없습니다. historyId=" + historyId));

        String newIndex = null;
        try {
            Set<String> previousTargets = drugIndexManager.resolveAliasTargets();

            newIndex = drugIndexManager.createTimestampedIndex();

            List<DrugDocument> documents = drugInfoRepository.findAll().stream()
                    .filter(drug -> drug.getItemSeq() != null)
                    .map(drugInfoConverter::toDocument)
                    .toList();
            drugIndexManager.bulkIndex(documents, newIndex);

            drugIndexManager.switchAlias(newIndex, previousTargets);
            drugIndexManager.deleteObsoleteIndices();

            history.success(documents.size(), newIndex);
            drugSyncHistoryRepository.save(history);
            log.info("약품 ES 재색인 완료 - index: {}, 색인: {}건", newIndex, documents.size());

        } catch (Exception e) {
            log.error("약품 ES 재색인 실패 - 기존 alias/인덱스는 유지됩니다. (historyId: {})", historyId, e);
            history.fail(e.getMessage());
            drugSyncHistoryRepository.save(history);
            cleanUpFailedIndex(newIndex);
        } finally {
            idempotencyKeyStore.release(REINDEX_LOCK_KEY);
        }
    }

    // alias 스왑 전에 실패했다면 새로 만든 인덱스는 아무도 참조하지 않으므로 삭제한다.
    private void cleanUpFailedIndex(String newIndex) {
        if (newIndex == null) {
            return;
        }
        if (drugIndexManager.resolveAliasTargets().contains(newIndex)) {
            return; // 스왑까지 성공한 뒤 후속 단계에서 실패한 경우 - 인덱스는 유효하므로 남긴다
        }
        try {
            drugIndexManager.deleteIndex(newIndex);
        } catch (Exception e) {
            log.warn("실패한 약품 인덱스 정리 실패 (무시): {} - {}", newIndex, e.getMessage());
        }
    }
}
