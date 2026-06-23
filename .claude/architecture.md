## 아키텍처 규칙

레이어: `Controller → Service → Repository`

- Entity를 Controller 계층에 노출 금지 — 반드시 DTO 변환
- 순환 참조 금지 — 도메인 간 의존은 단방향으로만
- 외부 API 연동은 별도 `client/` 패키지로 분리
- 공통 응답은 `ResponseUtils`로 래핑

---

## CQRS 규칙

- Service는 `command`(쓰기)와 `query`(읽기)로 분리
- Command: `@Transactional` 필수
- Query: `@Transactional(readOnly = true)` 사용
- 클래스 레벨에 `@Transactional(readOnly = true)`, 변경 메서드만 `@Transactional` 오버라이드

---

## 예외 처리 규칙

- 커스텀 예외: `CallCareException(ErrorCode)` 사용
- `ErrorCode`에 HTTP 상태코드와 메시지 함께 정의
- `GlobalExceptionHandler`에서 일괄 처리

```java
// 항상 이 패턴 사용
Entity entity = repository.findById(id)
    .orElseThrow(() -> new CallCareException(ErrorCode.ENTITY_NOT_FOUND));
```

---

## 공통 코드 패턴

### Service 패턴

```java
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class XxxQueryService {
    private final XxxRepository xxxRepository;
    private final XxxConverter xxxConverter;

    public XxxResponse getXxx(Long id) {
        Xxx xxx = xxxRepository.findById(id)
            .orElseThrow(() -> new CallCareException(ErrorCode.XXX_NOT_FOUND));
        return xxxConverter.toResponse(xxx);
    }
}

@Transactional
@Service
@RequiredArgsConstructor
public class XxxCommandService {
    private final XxxRepository xxxRepository;

    public void createXxx(XxxRequest request) {
        // dirty checking 활용, 명시적 save는 신규 엔티티에만 사용
    }
}
```

### Controller 패턴

```java
@Tag(name = "Xxx", description = "xxx 관련 API")
@RestController
@RequestMapping("/api/xxx")
@RequiredArgsConstructor
public class XxxController {

    @Operation(summary = "xxx 단건 조회", description = "id로 xxx를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<XxxResponse>> getXxx(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ResponseUtils.ok(xxxQueryService.getXxx(id)));
    }
}
```

### Swagger 컨벤션

- 컨트롤러 클래스: `@Tag(name = "도메인명", description = "한 줄 설명")`
- 각 메서드: `@Operation(summary = "짧은 요약", description = "상세 설명 (필요 시)")`
- description이 summary와 동일하면 생략 가능
- Response DTO: 클래스에 `@Schema(description = "응답 설명")`, 각 필드에 `@Schema(description = "필드 설명")`

```java
@Schema(description = "xxx 응답")
public record XxxResponse(
    @Schema(description = "ID") Long id,
    @Schema(description = "이름") String name
) {}
```

### Converter 패턴

- 위치: `domain/{도메인}/converter/XxxConverter.java`
- `@Component`로 Spring Bean 등록, 서비스에서 주입
- 메서드마다 변환 방향을 주석으로 명시: `// EntityA → DtoB (용도)`
- 여러 오버로드가 있을 경우 각각 주석으로 구분

```java
@Component
public class XxxConverter {

    // XxxEntity → XxxResponse (단건 조회 응답용)
    public XxxResponse toResponse(Xxx xxx) { ... }

    // XxxEntity → XxxDocument (ES 색인용)
    public XxxDocument toDocument(Xxx xxx) { ... }
}
```

---

## 코딩 컨벤션

- Lombok: `@RequiredArgsConstructor`, `@Getter`, `@Builder`, `@Slf4j`
- DTO는 `record` 또는 Lombok 클래스로 작성
- 엔티티는 `BaseEntity` 상속 (createdAt, updatedAt 자동 관리)
- 컨버터는 Spring Bean으로 주입 (`private final XxxConverter xxxConverter`)
- 상수는 `enum` 또는 `static final`, 매직 넘버 금지
- 모든 연관관계 `FetchType.LAZY`, 필요 시 `JOIN FETCH`
- Enum 필드는 `@Enumerated(EnumType.STRING)` 사용

---

## 보안 규칙

- Spring Security 기반 인증/인가 처리
- 인증이 필요한 API는 SecurityConfig에서 명시적으로 관리
- 민감 정보(비밀번호 등)는 절대 응답 DTO에 포함 금지
- 시크릿은 `application.yml`에 하드코딩 금지 — 환경변수로 주입
