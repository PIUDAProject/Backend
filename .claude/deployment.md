## Git 전략

- **기본 브랜치**: `develop` (main 브랜치 역할)
- 브랜치: `feature/{이슈번호}`, `fix/{이슈번호}`, `hotfix/{내용}`
- `develop`에서 브랜치 생성 → 작업 완료 후 `develop`으로 PR
- PR은 이슈 단위로 생성, 리뷰어 지정 필수
- `develop` 직접 push 금지 — PR + 리뷰 후 merge
- 커밋 메시지: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:` 접두사 사용

---

## 환경 설정

- `application.yml` — 공통 설정
- `application-local.yml` — 로컬 전용 (git 제외)
- `application-prod.yml` — 프로덕션 (환경변수로 시크릿 주입)
- DB: MySQL, 로컬 개발 시 Docker Compose 사용 권장

---

## 배포 규칙

- `main` 브랜치 merge 시 CI/CD 자동 트리거
- 배포 전 테스트 통과 필수
- 프로덕션 배포는 스테이징 검증 후 진행
- 롤백 필요 시 이전 jar 또는 Docker 이미지 태그로 재배포

```bash
./gradlew build -x test          # 테스트 제외 빌드 (CI용)
java -jar build/libs/callcare-*.jar
```

---

## 트러블슈팅

1. **Spring Security 403**: SecurityConfig의 antMatcher 경로 확인
2. **JPA LazyInitializationException**: 트랜잭션 범위 밖 연관관계 접근 여부 확인, JOIN FETCH 추가
3. **MySQL 연결 실패**: `application-local.yml` DB 설정 및 MySQL 실행 여부 확인
4. **빌드 실패**: `./gradlew clean build` 후 재시도
