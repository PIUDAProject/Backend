# Git 워크플로우 규칙

## 커밋 타이밍
- 커밋/푸시는 사용자가 명시적으로 요청할 때만 수행한다
- 파일 수정 후 임의로 커밋/푸시하지 않는다

## 커밋 분리 원칙
한 번에 전부 커밋하지 않는다. 아래 순서로 레이어별 분리:

1. `entity` / `enums` 변경 시
2. `dto` (request / response)
3. `converter`
4. `repository`
5. `service` (command / query 각각 별도)
6. `controller`
7. `config` (SecurityConfig 등)

## 커밋 메시지
- 본문은 한글로 작성한다
- 기술 용어(DTO, OCR, ES, JWT 등)는 영어 그대로 쓴다 — 괄호로 번역 추가하지 않는다
- 접두사: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`
