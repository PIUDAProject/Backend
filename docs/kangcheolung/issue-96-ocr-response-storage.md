# 이슈 #96 — OCR 응답 원본 저장 + 파서 회귀 테스트 기반

> 브랜치: `feature/96` · 관련 이슈: [PIUDAProject/Backend#96](https://github.com/PIUDAProject/Backend/issues/96)
>
> OCR 파싱 개선 1단계의 선행 작업. 후속: #B(표 처방전 좌표 파싱), #C(약봉투 다중 약)

---

## 1. 배경

약봉투·처방전 OCR 추출이 여러 서식에서 깨지는데, **실패를 재현할 수단이 없었다.**

| 문제 | 상세 |
|---|---|
| 응답 원본 유실 | `NaverOcrClient`가 응답을 `NaverOcrApiResponse`로 파싱한 뒤 원본을 버림. `OcrResult`엔 텍스트를 이어붙인 `raw_text`만 남고 좌표(`boundingPoly`)는 사라짐 |
| 테스트 0개 | `OcrParser` 단위 테스트가 없어, 서식 하나를 고치면 다른 서식이 회귀했는지 알 수 없음 |
| 개인정보 | `raw_text`에 환자 주민번호가 마스킹 없이 저장됨 |

이 이슈는 파싱 로직을 고치지 않는다. **회귀 테스트 기반**만 만든다. 응답 DTO(`OcrResultResponse`)
외부 계약은 그대로 두어 프론트 영향이 없다.

---

## 2. 변경

### 2-1. Naver 응답 원본 저장

`NaverOcrClient.callOcr()` — `bodyToMono(String.class)`로 원문을 받아 주입한 `ObjectMapper`로 파싱.
반환 타입을 `List<Field>` → `NaverOcrCallResult(fields, rawResponseJson)`로 변경.

```text
webClient.post()...bodyToMono(String.class)      ← 원문 문자열
  → objectMapper.readValue(raw, NaverOcrApiResponse.class)
  → new NaverOcrCallResult(extractFields(response), raw)
```

- WebClient 코덱 한도를 256KB(기본) → 10MB로 상향. 표 처방전은 필드(텍스트+좌표 4점)가 수백 개라
  기본 한도를 넘을 수 있음. `webClient.mutate().codecs(...)`로 이 클라이언트만 조정.
- `extractFields()` 검증(`inferResult != SUCCESS` → `OCR_API_ERROR`)은 그대로.
- 응답 원문을 재직렬화가 아니라 **문자열 그대로** 저장하는 이유: Naver의 `inferConfidence` 등
  우리 DTO에 없는 필드까지 보존해 회귀 코퍼스의 충실도를 유지.

`OcrResult` — `raw_response` TEXT 컬럼 + 빌더 파라미터. `ddl-auto: update`라 마이그레이션 파일 불필요.

### 2-2. 주민번호 마스킹

신규 `global/util/PiiMasker` — 주민번호만 대상.

```java
// 6자리 - 7자리, 뒷자리 첫 숫자 1~8 (공백 허용)
Pattern.compile("\\d{6}\\s*-\\s*[1-8]\\d{6}")  →  "******-*******"
```

- 형식이 고정이라 정규식으로 안전하게 잡힌다. 교부번호(`20260701-00042`, 뒤 5자리)는 패턴 불일치라 유지.
- 이름·생년월일은 형식이 없어 자동 식별이 어렵고, 저장 허용 범위(팀 합의)라 건드리지 않는다.
- `OcrCommandService`에서 `raw_text`·`raw_response` 둘 다 저장 직전에 통과.

### 2-3. 파서 회귀 테스트 하네스

| 파일 | 역할 |
|---|---|
| `test/resources/ocr/fixtures/*.json` | 저장된 Naver 응답(`NaverOcrApiResponse` 형태) |
| `test/resources/ocr/expected/manifest.json` | fixture → 기대 약 목록(`ParsedOcrData`) + `guard` 플래그 |
| `OcrFixtureLoader` | fixture/manifest 로드 |
| `OcrParserRegressionTest` | fixture별 `parse()` → precision/recall 리포트 + `guard` 단언 |

- `new OcrParser()` — 의존성이 없어 Mockito 불필요
- `guard=true`: 현재 정상 동작 → 결과가 어긋나면 **실패** (회귀 가드)
- `guard=false`: 아직 미달 → precision/recall만 **리포트**, 실패시키지 않음. 해당 이슈에서 fix + guard 승격

**fixture 2건 (초기)**

| 파일 | 출처 | 좌표 | 용도 |
|---|---|---|---|
| `pharmacy_receipt_yuseong.json` | 실제 유성온누리약국 영수증 (별표형) | 없음 | 현재 정상 — 회귀 가드 |
| `table_prescription_synth.json` | 합성 표 처방전. 헤더 `처방 의약품의`+`명칭` 분리를 의도적으로 재현 | 있음 | 이슈 B 대상 |

실제 약봉투·처방전 fixture는 팀이 사진에서 뽑아 이름 마스킹 후 추가한다.

---

## 3. 측정 (baseline)

```
[pharmacy_receipt_yuseong.json]  exact P=1.00 R=1.00 | name R=1.00 | 기대 4, 추출 4, 정확일치 4
  ↳ 유성온누리약국 영수증 (별표형, 좌표 없음) - 현재 정상 동작, 회귀 가드
[table_prescription_synth.json]  exact P=0.00 R=0.00 | name R=0.20 | 기대 5, 추출 1, 정확일치 0
  ↳ 합성 표 처방전 (좌표 포함) - 이슈 B 대상, 현재 미달
```

- `exact` = 4개 필드(이름/1회량/1일횟수/총일수) 완전 일치, `name` = 이름만 일치
- 표 처방전이 현재 얼마나 안 되는지가 수치로 고정됨 → **이슈 B가 R=1.00으로 뒤집는 게 목표**
- `OcrParserRegressionTest` 3 tests, 0 failures / `PiiMaskerTest` 통과

```bash
./gradlew test --tests "*OcrParserRegressionTest" --tests "*PiiMaskerTest"
```

---

## 4. 변경 파일

### 신규

| 파일 | 역할 |
|---|---|
| `domain/ocrresult/dto/NaverOcrCallResult` | OCR 호출 결과 — 파싱용 `fields` + 저장용 응답 원문 |
| `global/util/PiiMasker` | 주민번호 마스킹 |
| `test/.../ocrresult/fixture/OcrFixtureLoader` | fixture/manifest 로드 |
| `test/.../ocrresult/service/OcrParserRegressionTest` | 회귀 리포트 + 가드 |
| `test/.../global/util/PiiMaskerTest` | 마스킹 단위 테스트 |
| `test/resources/ocr/**` | fixture 2건 + manifest |

### 수정

| 파일 | 변경 |
|---|---|
| `NaverOcrClient` | 응답 원문 수신·파싱·반환, 코덱 한도 상향, 명시적 생성자 |
| `OcrResult` | `raw_response` 컬럼 + 빌더 |
| `OcrCommandService` | `NaverOcrCallResult` 반영, 저장 전 주민번호 마스킹 |

파싱 로직(`OcrParser`)·응답 DTO·보안 설정은 건드리지 않음.

---

## 5. 수동 검증 (로컬)

> 현재 `OcrController`·`MedicationController`에 로컬 테스트용 `userId` 하드코딩이 있음.
> **PR 전 `git checkout --`로 제거** (커밋 금지).

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

`POST /api/ocr` (form-data: `seniorId=1`, `image=<처방전 사진>`, `ocrType=PRESCRIPTION`)

- [ ] `ocr_result` 새 행의 `raw_response`에 좌표 포함 응답 JSON 저장
- [ ] 주민번호 있는 처방전 → `raw_text`·`raw_response` 모두 `******-*******`
- [ ] `OcrResultResponse` 필드 형태 변화 없음

---

## 6. 후속

- **이슈 B**: 표 처방전 좌표 파싱 복구 — 게이트 정규식 `\s*`→`[ \t]*`, `parseByCoordinates`
  헤더 x좌표 앵커 배정, `table_prescription_synth.json` guard 승격
- **이슈 C**: 약봉투 압축형(`약이름\n1정씩1회5일분` 반복) 다중 약
- **이슈 D**(범위 밖): ES/DrugInfo로 약 이름 검증
- fixture 코퍼스 확장: 실제 약봉투·처방전 사진 (이름 마스킹)
