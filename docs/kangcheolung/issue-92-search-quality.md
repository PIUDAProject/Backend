# 이슈 #92 — 약 검색 품질 재설계 (초성·edge n-gram·오타·관련도)

> 브랜치: `feature/92` · 관련 이슈: [PIUDAProject/Backend#92](https://github.com/PIUDAProject/Backend/issues/92)
>
> 전제: #90(무중단 재색인 인프라) — 매핑을 바꾼 뒤 `POST /api/admin/drugs/reindex` 한 번으로 반영

---

## 1. 배경

### 기존

`DrugDocument.itemName` = `text` 필드 1개(기본 애널라이저), 쿼리는 `match_phrase_prefix` 하나.

```text
"타이레놀정500밀리그램(아세트아미노펜)"
  → standard 토크나이저 → ["타이레놀정500밀리그램", "아세트아미노펜"]  (한글 런은 통짜)
```

### 안 되던 것

| 입력 | 기존 | 이유 |
|---|---|---|
| `ㅌㅇㄹㄴ` | ❌ 0건 | 초성 개념이 색인에 없음 |
| `타이레올` | ❌ 0건 | 통짜 토큰이라 fuzzy가 무력 |
| `서방정` (중간 단어) | ❌ 0건 | prefix만 매칭 |
| `아세트아미노펜` (괄호 속 성분명) | △ | standard가 괄호는 분리하나 정확 토큰만 매칭 |
| 결과 순서 | 무작위 | `match_phrase_prefix`는 스코어 변별력 약함 |

---

## 2. 설계 — `itemName` 한 값을 여러 방식으로 색인 + bool.should 조합

### 멀티필드

| 필드 | 색인 애널라이저 | 검색 애널라이저 | 용도 |
|---|---|---|---|
| `itemName` | `drug_search_analyzer` (standard + lowercase) | 동일 | 시작 매칭, 관련도 기준 |
| `itemName.keyword` | (keyword) | (keyword) | 이름 전체 완전 일치 |
| `itemName.autocomplete` | `drug_edge_ngram_analyzer` (edge_ngram 1~20) | `drug_search_analyzer` | 앞에서부터 타이핑하는 자동완성 |
| `itemName.ngram` | `drug_ngram_analyzer` (ngram 2~4) | `drug_ngram_analyzer` | 중간 단어·성분명·끝자리 오타 (ngram 겹침) |
| `itemNameChosung` | `keyword` (통짜 토큰) | `keyword` | 초성 검색 (`match_phrase_prefix` 로 접두 매칭 → 길이 제한 없음) |

- 애널라이저 정의: `src/main/resources/elasticsearch/drug-info-settings.json` (`@Setting`)
- `number_of_replicas: 0` 명시 (single-node → 클러스터 green)
- `edge_ngram`/`ngram` 토크나이저의 `token_chars: [letter, digit]` → `(`, `)`, `,`, `:` 는 토큰 경계
  → `"타치온정(글루타티온)"` 에서 `글루타티온` 이 독립적으로 ngram 색인됨

### 초성 처리

ES에 한글 자모 분해기가 없으므로 **색인 시점에 Java로** 생성한다.

```text
HangulChosungExtractor.extract("타이레놀정500")  →  "ㅌㅇㄹㄴㅈ500"
```

`DrugInfoConverter.toDocument` 에서 `itemNameChosung` 필드에 주입한다.
검색 시점에는 변환이 필요 없다 — 사용자가 입력한 `ㅌㅇㄹㄴ` 을 그대로 `itemNameChosung` 에 `match_phrase_prefix`(접두) 매칭한다.
`itemNameChosung` 을 통짜 토큰(`keyword`)으로 색인하므로 **입력 길이 제한이 없다**.
사용자가 완성형(`타이레`)을 입력하면 이 필드엔 매칭 0이라, 쿼리에 항상 포함해도 무해하다.

### 검색 쿼리 (`DrugSearchRepository.searchByItemName`)

```text
bool.should (minimum_should_match: 1):
  term(itemName.keyword)                            boost 20  ← 이름 전체 완전 일치 (최우선)
  match_phrase_prefix(itemName)                     boost 5   ← "입력한 그대로 시작"
  match(itemName.autocomplete)                      boost 2   ← edge n-gram 자동완성
  match(itemName, fuzziness=AUTO)                   boost 2   ← 짧은 약품명의 1글자 오타
  match(itemName.ngram, minimum_should_match=50%)   boost 1   ← 중간 단어·성분명·끝자리 오타
  match_phrase_prefix(itemNameChosung)             boost 3   ← 초성
```

boost로 완전/시작 일치가 상단에 오고, `ElasticsearchRepository` `@Query` 는 기본 `_score` 내림차순이라 서비스는 순서 그대로 매핑한다.

---

## 3. 변경 파일

### 신규

| 파일 | 역할 |
|---|---|
| `global/util/HangulChosungExtractor` | 완성형 한글 → 초성 문자열, `isChosungOnly` 판별 |
| `resources/elasticsearch/drug-info-settings.json` | 커스텀 애널라이저 3개 + replica 0 |
| `scripts/drug-search-bench.py` | before/after Recall@10 / MRR / latency 벤치 |

### 수정

| 파일 | 변경 |
|---|---|
| `DrugDocument` | `@Setting` 추가 / `itemName` → `@MultiField`(keyword, autocomplete, ngram) / `itemNameChosung` 필드 추가 |
| `DrugInfoConverter.toDocument` | `itemNameChosung = HangulChosungExtractor.extract(itemName)` |
| `DrugSearchRepository.searchByItemName` | `match_phrase_prefix` 단일 → `bool.should` 6절 |
| `DrugSearchQueryService.search` | 주석만 (시그니처·흐름 동일) |

폴백 경로(`DrugInfoController` → MySQL `LIKE`)와 색인 파이프라인(#90 `DrugIndexManager`)은 그대로.
`DrugIndexManager.createTimestampedIndex()`가 `@Setting`/`@MultiField`를 그대로 반영하므로 매핑 배포는 재색인 API 한 번.

---

## 4. before/after 측정 (로컬, 실제 4,745건)

`scripts/drug-search-bench.py` — 동일 4,745건으로 old(기존 매핑 + `match_phrase_prefix`) / new(신규 매핑 + `bool.should`) 벤치 인덱스를 만들어 라벨링 테스트셋 24개로 비교.

**정답 판별**: 결과 `itemName` 에 해당 쿼리의 기대 부분문자열 포함 여부.
**Recall@10** = (상위 10건 중 정답 수) / min(전체 정답 수, 10) · **MRR** = 1 / (첫 정답 순위).

| 지표 | old | new |
|---|---|---|
| **Recall@10 (평균)** | **0.471** | **0.978** |
| **MRR (평균)** | **0.521** | **0.979** |

| 유형 | old R@10 | new R@10 | 비고 |
|---|---|---|---|
| 정확/시작 (타이레놀, 게보린 등 10개) | 0.83 | 1.00 | new는 완전 일치가 1위 |
| 초성 (ㅌㅇㄹㄴ, ㄱㅂㄹ 등 4개) | 0.00 | 0.89 | 신규 |
| 오타 (타이레올, 게보른 등 4개) | 0.00 | 1.00 | 신규 |
| 중간 단어 (서방정, 현탁액) | 0.00 | 1.00 | 신규 |
| 성분명 (아세트아미노펜 등 4개) | 0.75 | 0.98 | ngram으로 개선 |

### latency (동일 쿼리 50회, size=20)

| 쿼리 | old p50 / p95 | new p50 / p95 |
|---|---|---|
| `타이레` | 1.5 / 2.9 ms | 2.1 / 3.0 ms |
| `게보린` | 1.4 / 1.8 ms | 1.9 / 2.7 ms |
| `아세트아미노펜` | 1.6 / 2.5 ms | 4.1 / 5.3 ms |

- should 절이 6개로 늘어 쿼리당 **약 +1~3ms** (ngram 매칭이 많은 성분명 쿼리가 가장 큼).
- 절대값은 여전히 한 자릿수 ms. 로컬 단일 노드 + 캐시된 상태 기준.
- 부팅 시 재색인 4,745건 ≈ 1.6초.

### 알려진 한계

**한국어 형태소 분석(nori) 미도입** → 약품명이 통짜 토큰이라 `타이래놀`(레→래, 중간 음절 오타)처럼 **중간 위치 오타**는 잘 못 잡는다.

- prefix 오타, 끝자리 오타, 짧은 이름 오타는 커버됨
- nori를 넣으면 `타이레놀정` → `타이레놀`+`정` 형태소 분리 → 중간 오타도 fuzzy로 잡힘
- nori는 ES 8.x 기본 이미지에 없어 커스텀 Docker 이미지 필요 → **2단계로 분리** (측정 후 필요하면)

`마그네슘`(성분명) 은 R@10 0.90 이지만 MRR 0.50 — 첫 결과가 마그네슘 제품이 아님(ngram 노이즈). `function_score` 로 조정 여지.

---

## 5. 수동 검증 절차 (로컬)

```bash
# 1. 새 매핑으로 재색인 (alias 있는 상태면 API, 없으면 부팅 시 자동)
#    로컬에서 강제로 새로 만들려면:
curl -s -XDELETE "localhost:9200/$(curl -s 'localhost:9200/_cat/aliases/drug_info?h=index')"
#    → 앱 재기동 시 DrugIndexingInitializer가 새 매핑으로 부트스트랩

# 2. 매핑 확인
curl -s localhost:9200/drug_info/_mapping | jq '.[].mappings.properties.itemName, .[].mappings.properties.itemNameChosung'

# 3. 검색
for q in 타이레놀 ㅌㅇㄹㄴ 타이레올 게보른 서방정 아세트아미노펜; do
  echo "$q:"; curl -s "localhost:8080/api/search/drugs?keyword=$q" | jq -r '.data[:3][].itemName'
done

# 4. before/after 벤치 (drug_info 가 신규 매핑이어야 함)
python3 scripts/drug-search-bench.py
```

---

## 6. 후속

- **nori 2단계**: 커스텀 ES 이미지(`elasticsearch-plugin install analysis-nori`) + `nori_tokenizer` 애널라이저 추가. 중간 음절 오타 + 관련도 개선
- **동의어**: 성분명 ↔ 대표 제품명 사전 (별도 이슈)
- **관련도 미세조정**: `function_score` 로 필드 길이 정규화 완화 (짧은 이름이 과하게 상위로 오는 경우)
- **테스트셋 확장**: 현재 24개 → 50개+, 실제 검색 로그 기반
