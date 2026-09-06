# 이슈 #100 — OCR 약 추출: 정규식/좌표 파서 → LLM 하이브리드

> 브랜치: `feature/100` · 관련: [PIUDAProject/Backend#100](https://github.com/PIUDAProject/Backend/issues/100)
>
> 선행: #96(응답 저장·회귀 하네스), #99(파서 고도화). fixture 9종이 "정답"을 정의.

---

## 1. 문제 상황

OCR(Naver CLOVA)은 글자를 잘 읽는다. 문제는 **그 글자를 `{약이름, 1회량, 1일횟수, 총일수}` 구조로 바꾸는 파싱**이 서식마다 깨진다는 것.

### 파서(정규식 + 좌표)의 구조적 한계

`OcrParser`는 서식별 분기의 사다리다:

```
별표 약봉투(*약이름)         → parseByPharmacyReceipt
라벨/압축 약봉투(1정씩2회5일분) → parseByTextSequential
보험코드 처방전(9자리+이름)    → parseByPrescriptionCode  (#99에서 추가)
표 처방전(명칭/투약량 헤더)    → parseByCoordinates
그 외                        → parseByRegex (단일 약)
```

#99까지 고쳐서 **알려진 서식 8종은 다 통과**하지만:

- 새 약국 POS 서식 하나가 나오면 → `if` 분기 추가 (밑 빠진 독)
- **뭉개진 약 이름을 교정 못 함** — `지스로먹스장`(OCR 오타)을 `지스로맥스정`으로 못 바꿈. 정규식은 OCR이 준 글자를 그대로 쓸 뿐
- 영수증처럼 상세 섹션 + 요약표가 섞인 서식에서 취약

### 목표

파싱 단계를 **LLM 추출**로 대체할 수 있는지 검증하고, 검증 결과에 따라 도입 방식을 정한다.
응답 스키마(`OcrResultResponse.parsedDrugs`)는 유지 → 프론트 영향 0.

---

## 2. 측정 — 파서 vs LLM

fixture 9종(실제 사진 5 + 합성 4)의 `rawText`/`fields`를 각 방식에 돌려 **약 단위 정확도**를 비교.
정답은 사람이 라벨링(`manifest.json`), 측정 코드는 `LlmDrugExtractorComparisonTest`(키 있을 때만 실행).

### 2-1. gpt-4o-mini + rawText (글자만)

| 서식 | 파서 | LLM |
|---|---|---|
| 표 처방전 | 4/4 | **숫자 뒤죽박죽** — `교부일로부터 7일`을 총투약일수 7로 오인 |
| 약봉투 | 4/4 · 9/9 | 개수 맞음, 이름·용량 형식 다름 |

→ rawText만 주면 표에서 숫자 위치 정보가 없어 컬럼 매핑 실패.

### 2-2. gpt-4o-mini + 좌표 (`텍스트 @(x,y)` 목록)

| 서식 | 결과 |
|---|---|
| 표 처방전 | **여전히 숫자 틀림** (`250mg`을 용량으로) |
| scattered 영수증 | 8약을 13개로 과추출 (성분명 분리) |

→ gpt-4o-mini는 좌표 리스트로 공간 추론을 못 함.

### 2-3. gpt-4o + 좌표 + **프롬프트 개선**

프롬프트 1차는 "제형어 빼라"고 해서 `아모잘탄정`→`아모잘탄` 처럼 접미사까지 제거됨.
→ "제형 접미사는 이름의 일부, 절대 떼지 마라 / 용량 표기·괄호 성분명만 제거 / 성분명만 있는 줄은 약 아님" 으로 수정.

| fixture | 파서 | gpt-4o |
|---|---|---|
| 별표 영수증 | 4/4 | 4/4 |
| 합성 표 처방전 | 5/5 | 5/5 |
| 압축 약봉투(합성) | 4/4 | 4/4 |
| 별표 약봉투(실제) | 4/4 | 3/4 (`코푸시럽` 용량 `1포` vs `1ml`) |
| **압축 약봉투(실제, 9약)** | 9/9 | **9/9** |
| **저화질 표 처방전** | **4/4** | **0/4** (숫자 매핑 실패) |
| **grid 영수증** | 4/5 | **5/5** — 파서가 놓친 약을 잡음 |
| scattered 영수증 | 3/8 | 이름 8/8 (횟수는 오독) |

---

## 3. 결정 — 하이브리드

측정이 말하는 것:

| 서식 | 이긴 쪽 | 이유 |
|---|---|---|
| 보험코드 줄 처방전 (저화질) | **파서** | 좌표를 알고리즘으로 정확히 계산. LLM은 좌표 텍스트로 표를 못 읽음 |
| 약봉투 | 무승부 | 파서 이미 정확(무료·즉시), LLM도 gpt-4o면 거의 동급 |
| 영수증 / 미지원 서식 | **LLM** | 파서가 놓치는 약을 잡고 이름이 깨끗 |

→ **"LLM으로 전면 전환"도 "파서 유지"도 아닌 하이브리드**:

```
OCR → 보험코드 줄(\d{8,10}\s+약이름)이 2개 이상인가?
  ├─ YES → 병원 처방전 → 파서 (parseByPrescriptionCode, 좌표)
  └─ NO  → 약봉투/영수증/그 외 → gpt-4o (좌표 텍스트 → 약 JSON)
                                    ↓ LLM 실패·약 0개
                                  파서 폴백
```

### 왜 "보험코드 줄"이 라우팅 신호인가

한국 처방전은 「국민건강보험 요양급여 규칙 별지 제9호」 법정 서식이라
`보험코드(8~10자리) + 제품명 + (내복/외용)` 줄이 고정으로 들어간다.
병원 EMR이 무엇이든 이 형식은 같고, 헤더 텍스트가 OCR로 뭉개져도 이 줄은 살아있다.
약봉투·영수증에는 없으므로 깔끔하게 갈린다.

---

## 4. 구현

### 신규

| 파일 | 역할 |
|---|---|
| `service/DrugExtractor` | `List<ParsedOcrData> extract(fields)`. 실패 시 예외 대신 빈 리스트(폴백 신호) |
| `service/LlmDrugExtractor` | `DrugExtractor` 구현. 키 미설정·API 오류 → 빈 리스트. sanity check(횟수 1~6, 일수 1~90 벗어나면 null) |
| `client/OpenAiClient` | fields → `"텍스트 @(x,y)"` 좌표 목록 → Chat Completions(`gpt-4o`, `temperature 0`, `json_object`) → `{"drugs":[...]}` |

### 수정

| 파일 | 변경 |
|---|---|
| `OcrParser` | `hasCodedPrescriptionLines(fields)` public 추가 (라우팅 신호) |
| `OcrCommandService` | 하이브리드 라우팅 + `usedLlm`/`method` 로깅 |
| `application.yml` | `openai.api-key/base-url/model` (`OPENAI_API_KEY` 없으면 LLM 자동 비활성 → 파서 전용) |

### 프롬프트 핵심 (`OpenAiClient.SYSTEM_PROMPT`)

- 입력은 `텍스트 @(x,y)` 목록, y 차이 15 이내면 같은 행
- 약 이름 오른쪽 같은 행의 한 자리 숫자를 x순으로 [투약량, 횟수, 일수]
- 제형 접미사(정·캡슐·서방정)는 이름의 일부 — **떼지 마라**
- 용량 표기(500mg)·괄호 성분명·제형만 나타내는 단어(코팅정)·성분명만 있는 줄은 제외
- "교부일로부터 N일"은 총투약일수 아님

### 응답/스키마

`OcrResultResponse.parsedDrugs` 형식 불변. `ocr_result`엔 여전히 첫 약만 저장(3-3 스키마).

---

## 5. 측정 재현

```bash
# 키 없으면 자동 스킵
OPENAI_API_KEY=sk-... ./gradlew test --tests "*LlmDrugExtractorComparisonTest" -Dgroups=integration
```

출력: fixture별 `파서 / LLM / 하이브리드` 정확도 + 라우팅 경로.

단위 테스트(키 불필요):
```bash
./gradlew test --tests "*OcrCommandServiceTest" --tests "*OcrParserRegressionTest"
```

---

## 6. 알려진 한계 · 후속

- **LLM 비결정성**: `temperature 0`이어도 gpt-4o가 run마다 미세하게 다른 출력(이름에 용량이 붙었다 안 붙었다). 결정론적인 파서와 다른 성질. 사용자 확인 UI가 최종 방어선이지만 "같은 사진 다른 결과" 리포트 가능성.
- **비용**: gpt-4o 호출당 약 15~20원. 약봉투·영수증이 LLM 경로라 자주 호출됨. 볼륨 커지면 gpt-4o-mini + 프롬프트 강화 또는 캐싱 검토.
- **이름 정규화 미완**: `지스로먹스장` → `지스로맥스정` 은 gpt-4o도 확실히 못 함. ES(`druginfo`) 대조로 교정 = 다음 이슈. 단 `druginfo` 4,745건이라 커버리지 제한적.
- **저화질 표 처방전**: 좌표가 흩어지면 파서도 흔들림(grid 영수증 4/5). 이미지→비전 LLM은 별도 검토.
- `ocr_result`에 첫 약만 저장 → 다중 약 이력 연결 안 됨 (스키마 개편 별도).
