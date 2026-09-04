# 이슈 #90 — DrugInfo ES 무중단 재색인 인프라 (alias + reindex API)

> 브랜치: `feature/90` · 관련 이슈: [PIUDAProject/Backend#90](https://github.com/PIUDAProject/Backend/issues/90)

---

## 1. 배경

### 기존 구조

약품 검색 데이터는 다음 흐름으로 준비된다.

```
drug_merged.json ─[DrugInfoDataInitializer @Order(1)]→ MySQL(drug_info, 12필드)
                 ─[DrugIndexingInitializer @Order(2)]→ Elasticsearch(drug_info, 6필드)
```

`DrugIndexingInitializer`는 앱 기동 시 `drug_info` **물리 인덱스**에 직접 색인했다.

### 문제

| # | 문제 | 영향 |
|---|---|---|
| 1 | 인덱스명이 `@Document(indexName = "drug_info")`로 고정 | 매핑/애널라이저를 바꾸려면 인덱스를 삭제·재생성해야 하고, 그동안 검색 다운타임 |
| 2 | 색인 로직이 `CommandLineRunner`에만 존재 | 재색인하려면 앱 재시작 필요 |
| 3 | 스킵 가드가 `esCount == dbCount` (건수 비교) | MySQL 내용이 바뀌어도 반영 안 됨 |
| 4 | 동시 재색인 방어 없음 | Blue/Green 동시 기동 시 양쪽 색인 시도 가능 |

### 왜 지금 필요한가

후속 이슈(약 검색 품질 재설계 — 초성/edge n-gram/오타 보정)가 **ES 매핑 변경**을 요구한다.
ES는 기존 인덱스의 필드 타입·애널라이저를 변경할 수 없으므로, 새 인덱스를 만들어 교체해야 한다.
이 교체를 **검색 다운타임 0**으로 하려면 alias 인프라가 선행돼야 한다.

---

## 2. 해결 방식 — alias 간접 참조 + 재색인 API

```
                        [before]                          [after]
애플리케이션 코드  ──→  drug_info(물리 인덱스)   코드 ──→ drug_info(alias) ──→ drug_info-20260904HHmmss(물리)
                                                                    ↑ 재색인 시 이 화살표를 새 물리 인덱스로 원자 스왑
```

- 코드(`DrugSearchRepository`, `DrugDocument`)는 항상 alias `drug_info`만 참조
- 물리 인덱스는 `drug_info-{yyyyMMddHHmmss}` 형태로 매 재색인마다 새로 생성
- 재색인: 새 물리 인덱스 생성 → 전체 색인 → **alias 원자 스왑** → 구 인덱스 정리
- 스왑하는 순간까지 사용자는 기존 인덱스로 검색을 계속함 → 다운타임 없음

---

## 3. 설계 결정

| 항목 | 선택 | 근거 |
|---|---|---|
| alias / 물리 인덱스명 | alias `drug_info`, 물리 `drug_info-yyyyMMddHHmmss` | 타임스탬프 접미사는 사전순 정렬 = 시간순 정렬 → 구 인덱스 정리가 단순 |
| `@Document` | `createIndex = false` 추가 | Spring Data가 `drug_info`라는 이름의 **물리 인덱스**를 자동 생성해버리면 alias를 못 만든다 |
| 동시 실행 차단 | **Redis 락** (`IdempotencyKeyStore.tryAcquire("drug:reindex", 10분)`) | 이미 존재하는 프리미티브 재사용. TTL이 붙어 있어 재색인 중 프로세스가 죽어도 락이 자동 만료됨 (병원 도메인의 DB status 방식은 stuck RUNNING을 수동 정리해야 함) |
| 비동기 실행 | `@Qualifier("applicationTaskExecutor")` (병원 동기화와 동일) | API는 이력 ID를 담아 즉시 반환(200, `ApiResponse.success`), 진행 상태는 이력 조회로 확인 |
| 구 인덱스 보관 | 최신 2개(현재 + 직전 1개) 유지, 나머지 삭제 | 문제 발생 시 직전 인덱스로 수동 롤백 여지 |
| 부팅 초기화 | alias 없으면 최초 1회만 생성·색인, 있으면 스킵 | 재색인은 명시적 API 액션으로 일원화 (`esCount == dbCount` 가드 제거) |
| SecurityConfig | **변경 없음** | `/api/admin/**`는 permitAll 목록에 없어 `.anyRequest().authenticated()`로 떨어짐 (병원 sync API도 동일) |
| ES 장애 시 | 초기화·재색인 모두 예외를 삼키고 로그만 | 검색은 MySQL LIKE 폴백으로 동작하므로 ES는 앱 필수 의존성이 아님 |

---

## 4. 클래스별 역할

### 신규

| 클래스 | 패키지 | 역할 |
|---|---|---|
| `DrugSyncStatus` | `druginfo.enums` | 재색인 상태 `RUNNING / SUCCESS / FAILED` |
| `DrugSyncHistory` | `druginfo.entity` | 재색인 배치 이력. `start()` / `success(count, index)` / `fail(msg)` |
| `DrugSyncHistoryRepository` | `druginfo.repository` | `findAllByOrderByStartedAtDesc(Pageable)` |
| `DrugIndexManager` | `druginfo.service` | ES 인덱스/alias 조작 헬퍼 (아래 상세) |
| `DrugReindexService` | `druginfo.service.command` | 재색인 오케스트레이션 (락 → 이력 → 색인 → 스왑 → 정리) |
| `DrugSyncHistoryQueryService` | `druginfo.service.query` | 이력 목록/단건 조회 |
| `DrugReindexStartResponse` | `druginfo.dto.response` | `{ historyId, status }` |
| `DrugSyncHistoryResponse` | `druginfo.dto.response` | 이력 조회 응답 |
| `DrugAdminController` | `druginfo.controller` | `/api/admin/drugs/**` 관리자 API |

### 수정

| 클래스 | 변경 |
|---|---|
| `DrugDocument` | `@Document(indexName = "drug_info", createIndex = false)` — alias 이름, 자동 인덱스 생성 비활성 |
| `DrugIndexingInitializer` | 건수 비교 색인 → **alias 부트스트랩**으로 전환 (`DrugIndexManager` 사용) |
| `DrugInfoConverter` | `toSyncHistoryResponse(DrugSyncHistory)` 추가 |
| `ErrorCode` | `DRUG_REINDEX_ALREADY_RUNNING`(409, DRUG-002), `DRUG_REINDEX_FAILED`(502, DRUG-003) |

---

## 5. `DrugIndexManager` 상세

Spring Data Elasticsearch `ElasticsearchOperations` + Elastic Java Client `ElasticsearchClient` 두 가지를 사용한다.

| 메서드 | 구현 | 용도 |
|---|---|---|
| `resolveAliasTargets()` | `elasticsearchClient.indices().getAlias(name=drug_info)` → 404면 빈 Set | alias가 현재 가리키는 물리 인덱스 집합 |
| `aliasExists()` | `resolveAliasTargets()` 비어있지 않음 | 부트스트랩 스킵 판단 |
| `dropLegacyConcreteIndexIfPresent()` | alias 없는데 `indexOps("drug_info").exists()` true면 delete | alias 전환 전 코드가 만든 물리 인덱스 정리 |
| `createTimestampedIndex()` | `indexOps(DrugDocument.class).createSettings()/createMapping()` → `indexOps(물리명).create(settings, mapping)` | `DrugDocument` 매핑을 적용한 새 물리 인덱스 생성, 이름 반환 |
| `bulkIndex(docs, indexName)` | `elasticsearchOperations.save(docs, IndexCoordinates.of(indexName))` | 물리 인덱스에 문서 색인 (Bulk) |
| `switchAlias(newIndex, previousIndices)` | `AliasActions`에 `Add(new)` + `Remove(old...)` 를 담아 **한 요청**으로 | alias 원자 스왑 |
| `deleteIndex(name)` | `indexOps(name).delete()` | 재색인 실패 시 잔존 인덱스 정리 |
| `deleteObsoleteIndices()` | `drug_info-*` 목록 → 최신순 정렬 → 최신 2개 + 현재 alias 대상 제외 삭제 | 구 인덱스 정리 (alias가 가리키는 인덱스는 절대 삭제 안 함) |

### alias 원자 스왑이 왜 무중단인가

ES `POST /_aliases`는 `actions` 배열의 모든 액션을 **하나의 원자 연산**으로 처리한다.

```json
{ "actions": [
  { "add":    { "index": "drug_info-20260904120000", "alias": "drug_info" } },
  { "remove": { "index": "drug_info-20260903090000", "alias": "drug_info" } }
]}
```

`add`와 `remove` 사이에 alias가 "아무것도 안 가리키는" 순간이 없다. 검색 요청은 스왑 직전이면 구 인덱스, 직후면 새 인덱스로 라우팅되며 실패하는 요청이 없다.

---

## 6. 재색인 흐름 (`DrugReindexService`)

```
startReindex()                          [동기, API 스레드]
  ├─ idempotencyKeyStore.tryAcquire("drug:reindex", 10m)
  │     └─ 실패 → CallCareException(DRUG_REINDEX_ALREADY_RUNNING)  → 409
  ├─ DrugSyncHistory.start() 저장 (status=RUNNING)
  ├─ taskExecutor.execute(() -> executeReindex(historyId))
  │     └─ 제출 실패(TaskRejectedException) → history.fail(), 락 해제, DRUG_REINDEX_FAILED(502)
  └─ return { historyId, RUNNING }       → 200 즉시 응답 (ApiResponse.success)

executeReindex(historyId)                [비동기, applicationTaskExecutor]
  try:
    previousTargets = resolveAliasTargets()
    newIndex        = createTimestampedIndex()          // drug_info-yyyyMMddHHmmss
    documents       = drugInfoRepository.findAll()
                        .filter(itemSeq != null)
                        .map(converter::toDocument)
    bulkIndex(documents, newIndex)
    switchAlias(newIndex, previousTargets)              // 원자 스왑
    deleteObsoleteIndices()                             // 최신 2개 + 현재 alias 대상 유지
    history.success(documents.size(), newIndex)
  catch Exception:
    history.fail(message)
    cleanUpFailedIndex(newIndex)   // 스왑 전 실패면 새 인덱스는 아무도 안 봄 → 삭제
  finally:
    idempotencyKeyStore.release("drug:reindex")
```

### 실패 안전성

- **스왑 전 실패**: 새 인덱스만 남고 alias는 구 인덱스를 계속 가리킴 → `cleanUpFailedIndex`가 새 인덱스 삭제 → 완전 원상복구
- **스왑 후 후속 단계 실패**: alias는 이미 새 인덱스를 가리킴(정상) → 새 인덱스는 남김
- **프로세스 크래시**: Redis 락은 TTL(10분)로 자동 만료 → 이후 재색인 요청 가능. RUNNING 이력은 남지만 락과 무관 (이력은 관측용)

---

## 7. 부팅 동작 (`DrugIndexingInitializer`)

```
run()  @Order(2)
  try:
    if aliasExists():  log "생략"; return           // 이미 구성됨
    dropLegacyConcreteIndexIfPresent()              // 구버전 잔존 물리 인덱스 제거
    newIndex = createTimestampedIndex()
    documents = drugInfoRepository.findAll()...
    bulkIndex(documents, newIndex)
    switchAlias(newIndex, Set.of())                 // 최초 생성이라 remove 대상 없음
  catch Exception:
    log.error(...)                                  // 부팅 계속, 검색은 MySQL 폴백
```

- alias가 이미 있으면 부팅 시 **아무것도 하지 않는다** — 데이터 갱신은 재색인 API 책임
- `esCount == dbCount` 가드는 제거됨 (알고리즘상 부트스트랩은 alias 유무만 본다)

---

## 8. API

전부 `/api/admin/drugs/**` — `SecurityConfig`에서 permitAll이 아니므로 인증 필요.

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| `POST` | `/api/admin/drugs/reindex` | 재색인 시작 (비동기) | `200` + `{ historyId, status: "RUNNING" }` / 이미 실행 중이면 `409 DRUG-002` / 작업 제출 실패 시 `502 DRUG-003` |
| `GET` | `/api/admin/drugs/reindex/history?limit=10` | 최근 이력 목록 (최신순) | `DrugSyncHistoryResponse[]` |
| `GET` | `/api/admin/drugs/reindex/{historyId}` | 특정 재색인 상태 | `DrugSyncHistoryResponse` / 없으면 `404` |

### DrugSyncHistoryResponse

```json
{
  "id": 1,
  "status": "SUCCESS",
  "startedAt": "2026-09-04T12:00:00",
  "finishedAt": "2026-09-04T12:00:03",
  "indexedCount": 4745,
  "targetIndex": "drug_info-20260904120000",
  "errorMessage": null
}
```

---

## 9. 수동 검증 절차 (로컬)

```bash
# 0. 로컬 인프라 기동
docker compose up -d mysql elasticsearch redis
./gradlew bootRun --args='--spring.profiles.active=local'
```

```bash
# 1. 부팅 후 alias가 생성됐는지 확인
curl -s localhost:9200/_alias/drug_info | jq
# → { "drug_info-20260904xxxxxx": { "aliases": { "drug_info": {} } } }

# 2. 검색이 alias로 동작하는지
curl -s 'localhost:8080/api/search/drugs?keyword=타이레놀' | jq '.data | length'

# 3. 재색인 트리거 (JWT 필요 — 로컬 토큰 발급 후 Authorization 헤더)
curl -s -X POST localhost:8080/api/admin/drugs/reindex \
  -H "Authorization: Bearer $TOKEN" | jq
# → { "success": true, "data": { "historyId": 1, "status": "RUNNING" } }

# 4. 진행 상태
curl -s localhost:8080/api/admin/drugs/reindex/1 -H "Authorization: Bearer $TOKEN" | jq

# 5. 재색인 후 alias가 새 인덱스를 가리키는지 (타임스탬프가 바뀜)
curl -s localhost:9200/_alias/drug_info | jq

# 6. 물리 인덱스가 최신 2개만 남았는지
curl -s 'localhost:9200/_cat/indices/drug_info-*?v'

# 7. 재색인 중 재요청 시 409
curl -s -X POST localhost:8080/api/admin/drugs/reindex -H "Authorization: Bearer $TOKEN" -w '\n%{http_code}\n'
# (재색인이 매우 빨라 재현이 어려우면 Redis에 직접 `SET idem:drug:reindex 1 EX 60` 후 호출)
```

---

## 10. 테스트

`DrugReindexServiceTest` (단위, Mockito):

| 케이스 | 검증 |
|---|---|
| 락 선점 시 | `DRUG_REINDEX_ALREADY_RUNNING`, 이력 미저장 |
| 정상 흐름 | `createTimestampedIndex → bulkIndex → switchAlias → deleteObsoleteIndices` 호출, 이력 `SUCCESS`, 락 해제 |
| 스왑 실패 | 이력 `FAILED`, 실패 인덱스 `deleteIndex` 호출, 락 해제 |

ES 통합 테스트는 인프라 부재로 생략, 위 수동 절차로 대체.

---

## 11. 트러블슈팅

| 증상 | 원인 | 대응 |
|---|---|---|
| 부팅 로그 `alias-not-found` 후 색인 안 됨 | `drug_info`가 alias 아닌 물리 인덱스로 이미 존재 (구버전) | `dropLegacyConcreteIndexIfPresent`가 자동 처리. 안 되면 `curl -XDELETE localhost:9200/drug_info` 후 재기동 |
| 재색인이 항상 409 | 이전 재색인이 락을 해제 못 하고 프로세스 종료 | 최대 10분 후 TTL 만료. 즉시 필요 시 `redis-cli DEL idem:drug:reindex` |
| `drug_info-*` 인덱스가 계속 쌓임 | `deleteObsoleteIndices` 실패 (권한/네트워크) | 로그 확인 후 수동 삭제. 다음 재색인에서 재시도됨 |
| 검색 결과가 재색인 후에도 옛날 데이터 | alias 스왑은 됐으나 클라이언트/프록시 캐시 | ES 레벨 캐시 아님. 앱 재시작 불필요, 잠시 후 재조회 |

---

## 12. 후속 이슈와의 연결

- **검색 품질 재설계**: `createTimestampedIndex()`가 `DrugDocument`의 매핑을 그대로 반영하므로, `DrugDocument`에 `@Setting`/멀티필드를 추가하고 `POST /api/admin/drugs/reindex` 한 번이면 무중단 반영
- **식약처 실시간 동기화**: 동기화 완료 후 `DrugReindexService.startReindex()` 호출로 ES 반영. `DrugSyncHistory`를 `type` 컬럼으로 확장해 REINDEX/SYNC 구분 가능
