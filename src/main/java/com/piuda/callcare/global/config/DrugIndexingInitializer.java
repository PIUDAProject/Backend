package com.piuda.callcare.global.config;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.document.DrugDocument;
import com.piuda.callcare.domain.druginfo.repository.DrugInfoRepository;
import com.piuda.callcare.domain.druginfo.service.DrugIndexManager;
import com.piuda.callcare.global.config.redis.IdempotencyKeyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 약품 검색 alias 부트스트랩.
 * <p>
 * alias {@code drug_info}가 없으면(최초 배포) 물리 인덱스를 하나 만들어 전체 색인하고 alias를 건다.
 * alias가 이미 있으면 아무것도 하지 않는다 — 이후 재색인은 관리자 API({@code POST /api/admin/drugs/reindex})로만 수행한다.
 * <p>
 * 여러 인스턴스가 동시에 기동해도 부트스트랩이 한 번만 돌도록 재색인과 동일한 Redis 락을 사용한다.
 * ES 장애 시에는 로그만 남기고 부팅을 계속한다(검색은 MySQL 폴백으로 동작).
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DrugIndexingInitializer implements CommandLineRunner {

    private static final Duration BOOTSTRAP_LOCK_TTL = Duration.ofMinutes(5);

    private final DrugInfoRepository drugInfoRepository;
    private final DrugIndexManager drugIndexManager;
    private final DrugInfoConverter drugInfoConverter;
    private final IdempotencyKeyStore idempotencyKeyStore;

    @Override
    public void run(String... args) {
        try {
            if (drugIndexManager.aliasExists()) {
                log.info("약품 검색 alias '{}' 존재 - 초기 색인 생략", DrugIndexManager.ALIAS);
                return;
            }
            bootstrapUnderLock();
        } catch (Exception e) {
            log.error("약품 검색 초기 색인 실패 - 검색 기능이 제한될 수 있습니다.", e);
        }
    }

    private void bootstrapUnderLock() {
        if (!idempotencyKeyStore.tryAcquire(DrugIndexManager.REINDEX_LOCK_KEY, BOOTSTRAP_LOCK_TTL)) {
            log.info("다른 인스턴스가 약품 검색 색인을 진행 중 - 부트스트랩 생략");
            return;
        }
        try {
            if (drugIndexManager.aliasExists()) { // 락 획득 사이에 다른 인스턴스가 끝냈을 수 있다
                log.info("약품 검색 alias가 이미 생성됨 - 부트스트랩 생략");
                return;
            }

            drugIndexManager.dropLegacyConcreteIndexIfPresent();

            long dbCount = drugInfoRepository.count();
            log.info("약품 검색 alias 최초 생성 및 색인 시작 (DB: {}건)", dbCount);

            String newIndex = drugIndexManager.createTimestampedIndex();

            List<DrugDocument> documents = drugInfoRepository.findAll()
                    .stream()
                    .filter(drug -> drug.getItemSeq() != null)
                    .map(drugInfoConverter::toDocument)
                    .toList();
            drugIndexManager.bulkIndex(documents, newIndex);

            drugIndexManager.switchAlias(newIndex, Set.of());
            log.info("약품 검색 최초 색인 완료: {}건 → {}", documents.size(), newIndex);
        } finally {
            idempotencyKeyStore.release(DrugIndexManager.REINDEX_LOCK_KEY);
        }
    }
}
