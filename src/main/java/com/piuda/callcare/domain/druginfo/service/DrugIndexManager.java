package com.piuda.callcare.domain.druginfo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.piuda.callcare.domain.druginfo.document.DrugDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 약품 검색 인덱스의 alias 기반 무중단 재색인을 위한 Elasticsearch 인덱스/alias 조작 헬퍼.
 * <p>
 * 애플리케이션 코드는 항상 alias {@code drug_info}만 참조하고, 실제 데이터는 타임스탬프가 붙은
 * 물리 인덱스({@code drug_info-yyyyMMddHHmmssSSS})에 들어간다. 재색인은 새 물리 인덱스를 만들어
 * 색인한 뒤 alias를 원자적으로 스왑하므로, 검색 다운타임이 발생하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrugIndexManager {

    public static final String ALIAS = "drug_info";
    // 재색인/부트스트랩 동시 실행을 막는 Redis 락 키 (DrugReindexService, DrugIndexingInitializer 공용)
    public static final String REINDEX_LOCK_KEY = "drug:reindex";
    private static final String PHYSICAL_INDEX_PATTERN = ALIAS + "-*";
    // 밀리초까지 붙여 연속 재색인 간 인덱스명이 겹치지 않게 한다. 고정 폭이라 사전순 = 시간순 정렬도 유지된다.
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int KEEP_RECENT_INDICES = 2; // 현재 + 롤백용 직전 1개
    private static final int ALIAS_NOT_FOUND = 404;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    /**
     * alias가 가리키는 물리 인덱스 집합. alias가 아직 없으면(404) 빈 집합을 반환한다.
     * 네트워크/인증/권한/서버 오류는 alias 부재로 오인하지 않고 그대로 전파한다
     * (오인하면 switchAlias가 구 인덱스를 제거하지 않아 alias가 여러 인덱스를 가리킬 수 있다).
     */
    public Set<String> resolveAliasTargets() {
        try {
            return elasticsearchClient.indices().getAlias(g -> g.name(ALIAS)).result().keySet();
        } catch (ElasticsearchException e) {
            if (e.status() == ALIAS_NOT_FOUND) {
                log.debug("alias {} 없음 - 아직 생성 전으로 간주", ALIAS);
                return Set.of();
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("alias " + ALIAS + " 조회 실패", e);
        }
    }

    public boolean aliasExists() {
        return !resolveAliasTargets().isEmpty();
    }

    // pre-alias 코드가 만들어 둔 동일 이름의 물리 인덱스가 남아 있으면 삭제한다.
    public void dropLegacyConcreteIndexIfPresent() {
        if (aliasExists()) {
            return;
        }
        IndexOperations ops = elasticsearchOperations.indexOps(IndexCoordinates.of(ALIAS));
        if (ops.exists()) {
            log.warn("alias가 아닌 물리 인덱스 '{}'가 존재하여 삭제합니다 (alias 전환 전 잔존물).", ALIAS);
            ops.delete();
        }
    }

    // DrugDocument 매핑을 적용한 새 물리 인덱스를 만들고 이름을 반환한다.
    public String createTimestampedIndex() {
        String indexName = ALIAS + "-" + LocalDateTime.now().format(INDEX_SUFFIX);

        IndexOperations entityOps = elasticsearchOperations.indexOps(DrugDocument.class);
        Map<String, Object> settings = entityOps.createSettings();
        Document mapping = entityOps.createMapping();

        IndexOperations targetOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
        boolean created = targetOps.create(settings, mapping);
        if (!created) {
            // 이미 존재하는 인덱스에 색인하면 삭제분이 반영되지 않고 alias 스왑에서도 제거를 건너뛴다.
            throw new IllegalStateException("약품 물리 인덱스 생성 실패 (이미 존재): " + indexName);
        }
        log.info("새 약품 물리 인덱스 생성: {}", indexName);
        return indexName;
    }

    // 지정한 물리 인덱스에 문서를 색인하고, alias 스왑 후 바로 검색되도록 refresh 한다.
    public void bulkIndex(List<DrugDocument> documents, String indexName) {
        IndexCoordinates target = IndexCoordinates.of(indexName);
        if (!documents.isEmpty()) {
            elasticsearchOperations.save(documents, target);
        }
        // 색인 응답만으로는 문서가 검색 가능(refresh 완료) 상태임을 보장하지 않는다.
        // alias 스왑 직후 새 인덱스 검색이 비어 보이지 않도록 명시적으로 refresh 한다.
        elasticsearchOperations.indexOps(target).refresh();
    }

    /**
     * alias가 새 인덱스만 가리키도록 원자적으로 스왑한다.
     * (add new + remove old 를 한 요청으로 처리하므로 그 사이 alias가 비는 순간이 없다)
     */
    public void switchAlias(String newIndex, Set<String> previousIndices) {
        AliasActions actions = new AliasActions();
        actions.add(new AliasAction.Add(
                AliasActionParameters.builder().withIndices(newIndex).withAliases(ALIAS).build()));
        for (String old : previousIndices) {
            if (!old.equals(newIndex)) {
                actions.add(new AliasAction.Remove(
                        AliasActionParameters.builder().withIndices(old).withAliases(ALIAS).build()));
            }
        }
        elasticsearchOperations.indexOps(IndexCoordinates.of(newIndex)).alias(actions);
        log.info("alias '{}' → '{}' 스왑 완료 (이전: {})", ALIAS, newIndex, previousIndices);
    }

    // 특정 물리 인덱스를 삭제한다 (재색인 실패 시 잔존 인덱스 정리용).
    public void deleteIndex(String indexName) {
        elasticsearchOperations.indexOps(IndexCoordinates.of(indexName)).delete();
        log.info("약품 물리 인덱스 삭제: {}", indexName);
    }

    // drug_info-* 물리 인덱스 중 최신 KEEP_RECENT_INDICES개와 현재 alias 대상은 남기고 나머지를 삭제한다.
    public void deleteObsoleteIndices() {
        Set<String> aliasTargets = resolveAliasTargets();
        List<String> physicalIndices = listPhysicalIndices();
        physicalIndices.sort(Comparator.reverseOrder()); // 타임스탬프 접미사 → 최신순

        for (int i = 0; i < physicalIndices.size(); i++) {
            String index = physicalIndices.get(i);
            if (i < KEEP_RECENT_INDICES || aliasTargets.contains(index)) {
                continue; // 최신 N개 또는 현재 alias가 가리키는 인덱스는 보존
            }
            try {
                elasticsearchOperations.indexOps(IndexCoordinates.of(index)).delete();
                log.info("오래된 약품 인덱스 삭제: {}", index);
            } catch (Exception e) {
                log.warn("오래된 약품 인덱스 삭제 실패 (무시하고 진행): {} - {}", index, e.getMessage());
            }
        }
    }

    private List<String> listPhysicalIndices() {
        try {
            return new ArrayList<>(elasticsearchClient.indices()
                    .get(g -> g.index(PHYSICAL_INDEX_PATTERN).ignoreUnavailable(true).allowNoIndices(true))
                    .result()
                    .keySet());
        } catch (Exception e) {
            log.warn("약품 물리 인덱스 목록 조회 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
