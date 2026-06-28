---
globs: "docker-compose*.yml, deploy.sh, .github/workflows/**, Dockerfile"
---

# 배포 규칙

## Git 전략
- 기본 브랜치: `develop`
- 브랜치: `feature/{이슈번호}`, `fix/{이슈번호}`, `hotfix/{내용}`
- `develop`에서 브랜치 생성 → 작업 완료 후 `develop`으로 PR
- PR은 이슈 단위로 생성, 리뷰어 지정 필수
- `develop` 직접 push 금지 — PR + 리뷰 후 merge
- 커밋 메시지: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:` 접두사

## 환경 설정
- `application.yml` — 공통 설정
- `application-local.yml` — 로컬 전용 (git 제외)
- `application-prod.yml` — 프로덕션 (환경변수로 시크릿 주입)

## 배포
- `main` 브랜치 merge 시 CI/CD 자동 트리거
- Blue/Green 무중단 배포 (Nginx)
- 배포 전 테스트 통과 필수
- 롤백: 이전 Docker 이미지 태그로 재배포

```bash
./gradlew build -x test   # CI용 (테스트 제외)
java -jar build/libs/callcare-*.jar
```

## 트러블슈팅
- LazyInitializationException: 트랜잭션 범위 밖 연관관계 접근, JOIN FETCH 추가
- MySQL 연결 실패: `application-local.yml` DB 설정 및 MySQL 실행 여부 확인
- 빌드 실패: `./gradlew clean build` 후 재시도
