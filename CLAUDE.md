# CLAUDE.md — callcare

## 프로젝트 개요
의약품 정보 및 복약 관리 서비스 백엔드

- **Framework**: Spring Boot 3.5.14
- **Language**: Java 17
- **Build**: Gradle
- **DB**: MySQL
- **Package**: `com.piuda.callcare`

---

## 프로젝트 구조

```
src/main/java/com/piuda/callcare/
├── global/
│   ├── common/          # 공통 응답 (ApiResponse, ErrorResponse, BaseEntity)
│   ├── config/          # 설정 (SecurityConfig 등)
│   └── exception/       # 전역 예외 (CallCareException, ErrorCode, GlobalExceptionHandler)
└── domain/
    └── {도메인}/
        ├── entity/
        ├── repository/
        ├── service/
        │   ├── command/     # 상태 변경 (Create, Update, Delete)
        │   └── query/       # 조회 전용
        ├── controller/
        ├── dto/
        │   ├── request/
        │   └── response/
        ├── converter/       # Entity ↔ DTO 변환 (Spring Bean으로 주입)
        └── enums/
```

**도메인 목록**: `user`, `hospital`, `druginfo`, `ocrresult`, `medication`, `drugconflict`, `calllog`

---

## 주요 명령어

```bash
./gradlew build
./gradlew clean build
./gradlew bootRun
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test
./gradlew test -Dgroups=integration
./gradlew test --tests *XxxService*
```

---

@.claude/architecture.md
@.claude/testing.md
@.claude/deployment.md
