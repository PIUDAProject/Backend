# 외부 API 연동 규칙

---

## 식약처 공공데이터 API (DrugInfo)

### 연동 방식
- 식약처 API를 직접 실시간 호출하지 않고, **공공데이터를 JSON 파일로 가공 후 앱 기동 시 MySQL에 캐싱**
- 파일 위치: `src/main/resources/data/drug_merged.json`
- 기동 시 `DrugInfoDataInitializer`(Order 1) → MySQL 저장 → `DrugIndexingInitializer`(Order 2) → ES 색인

### 저장 필드
| 필드 | 설명 |
|------|------|
| `itemSeq` | 품목기준코드 (고유 식별자) |
| `itemName` | 약품명 |
| `entpName` | 제조사명 |
| `efcyQesitm` | 효능효과 |
| `useMethodQesitm` | 용법용량 (복용방법) |
| `atpnQesitm` | 주의사항 |
| `seQesitm` | 부작용 |
| `intrcQesitm` | 상호작용 (약물 충돌 분석에 사용) |
| `depositMethodQesitm` | 보관법 |
| `itemImage` | 약 이미지 URL |
| `prductType` | 약 종류 |
| `spcltyPblc` | 전문/일반 의약품 구분 |

### 캐싱 전략
- 데이터 건수 약 4,700건, 고정 데이터 (공공데이터라 수정 없음)
- MySQL에 이미 데이터 있으면 초기화 생략
- ES 건수 == DB 건수이면 색인 생략 (매 기동마다 재색인 방지)

### ES 색인 필드
ES(`drug_info` 인덱스)에는 검색에 필요한 필드만 저장:
`itemSeq`, `itemName`(Text), `entpName`, `prductType`, `spcltyPblc`, `itemImage`

### 자동완성 검색 방식
- `match_phrase_prefix` 쿼리 사용
- 최대 20건 반환
- API: `GET /api/search/drugs?keyword={약품명}`

---

## Naver CLOVA OCR API

### 설정
- Invoke URL: `${NAVER_OCR_INVOKE_URL}` (application.yml)
- Secret Key: `${NAVER_OCR_SECRET_KEY}` (application.yml)
- 도메인 타입: General

### 요청 형식
- Method: `POST {invokeUrl}`
- Content-Type: `multipart/form-data`
- Header: `X-OCR-SECRET: {secretKey}`

**message 파트 (JSON string):**
```json
{
  "version": "V2",
  "requestId": "uuid",
  "timestamp": 1234567890,
  "lang": "ko",
  "images": [{ "format": "jpg", "name": "image" }]
}
```

**file 파트:** 이미지 바이너리 (jpg, png 등)

### 응답 형식
```json
{
  "images": [
    {
      "inferResult": "SUCCESS",
      "fields": [
        { "inferText": "인식된 텍스트", "lineBreak": false }
      ]
    }
  ]
}
```
- `inferResult`: `SUCCESS` / `FAILURE`
- `lineBreak`: true면 줄 끝, false면 이어지는 텍스트 → true일 때 `\n`, false일 때 공백으로 이어 붙임

### 파싱 전략
| OcrType | 파싱 방식 |
|---------|----------|
| PRESCRIPTION | 처방전 — 약품명/복용횟수/용량/일수 정규식 추출 |
| DRUG_BAG | 약봉투 — PRESCRIPTION과 동일한 패턴 |
| DRUG_BOX | 약곽 — PRESCRIPTION과 동일한 패턴 |

### 파싱 한계
- OCR 인식률에 따라 파싱 실패 가능 → 각 필드 null 허용
- 처방전 형식이 병원마다 다를 수 있음 → 정규식 지속 보완 필요
- `rawText`는 `OcrResult`에 저장 → 파싱 실패 시 클라이언트에서 수동 입력 가능
- **표 형태 처방전 구조적 한계**: OCR이 컬럼 단위로 텍스트를 읽어 "헤더 → 숫자 전체 → 약 이름" 순으로 반환함. 약 이름과 수치(1일투여횟수, 총투약일수)가 텍스트 순서상 멀리 떨어져 정규식 매칭 불가. `timesPerDay`/`totalDays`는 null 반환 → boundingPoly 좌표 기반 행 매칭으로 개선 예정 (3-3 백로그)

### API 엔드포인트
- `POST /api/ocr/prescription` — 처방전
- `POST /api/ocr/package?ocrType=DRUG_BAG` — 약봉투 (기본값)
- `POST /api/ocr/package?ocrType=DRUG_BOX` — 약곽
