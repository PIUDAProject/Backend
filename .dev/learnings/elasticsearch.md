# Elasticsearch 관련

## @NoArgsConstructor + @AllArgsConstructor 필수
- `@Builder`만 있으면 기본 생성자가 없어서 ES가 검색 결과를 Document 객체로 역직렬화할 때 런타임 오류 발생 가능
- `@NoArgsConstructor` + `@AllArgsConstructor` 를 함께 추가해야 안전
- 적용 파일: `HospitalDocument`, `DrugDocument`

## 재색인 시 deleteAll() 먼저 해야 하는 경우
- `saveAll()`만 하면 DB에서 삭제된 데이터가 ES에 계속 남아있음
- 데이터가 변경될 수 있는 경우(Hospital 등): `deleteAll()` 후 `saveAll()`
- 고정 데이터(DrugInfo 공공데이터 등): `deleteAll()` 불필요, 건수 비교로 스킵

## 예외 로깅은 e.getMessage() 대신 e 전체를 넘겨야 함
- `log.error("...: {}", e.getMessage())` → 메시지 텍스트만 남음
- `log.error("...", e)` → 스택트레이스 포함, 장애 원인 추적 가능
