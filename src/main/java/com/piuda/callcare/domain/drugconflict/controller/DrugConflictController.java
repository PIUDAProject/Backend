package com.piuda.callcare.domain.drugconflict.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictDetailResponse;
import com.piuda.callcare.domain.drugconflict.dto.response.DrugConflictResponse;
import com.piuda.callcare.domain.drugconflict.service.command.DrugConflictCommandService;
import com.piuda.callcare.domain.drugconflict.service.query.DrugConflictQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "DrugConflict", description = "약물 충돌(상호작용) 분석 API")
@RestController
@RequestMapping("/api/conflicts")
@RequiredArgsConstructor
public class DrugConflictController {

    private final DrugConflictCommandService drugConflictCommandService;
    private final DrugConflictQueryService drugConflictQueryService;

    @Operation(summary = "약물 충돌 분석 실행",
            description = "어르신의 활성 약 전체 쌍을 검사해 새 충돌을 저장하고, 저장된 충돌 목록을 반환합니다. 리포트 화면 진입 시 호출.")
    @PostMapping("/analysis")
    public ResponseEntity<ApiResponse<List<DrugConflictResponse>>> analyze(
            @RequestParam Long seniorId
    ) {
        drugConflictCommandService.analyze(seniorId);
        return ResponseUtils.ok(drugConflictQueryService.getConflicts(seniorId));
    }

    @Operation(summary = "약물 충돌 목록 조회",
            description = "저장된 충돌을 카드 목록으로 반환합니다. 충돌이 없으면 빈 배열을 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DrugConflictResponse>>> getConflicts(
            @RequestParam Long seniorId
    ) {
        return ResponseUtils.ok(drugConflictQueryService.getConflicts(seniorId));
    }

    @Operation(summary = "약물 충돌 상세 조회",
            description = "카드 클릭 시 충돌 상세(설명 등)를 조회합니다. 확인 처리한 충돌도 조회됩니다.")
    @GetMapping("/{conflictId}")
    public ResponseEntity<ApiResponse<DrugConflictDetailResponse>> getConflictDetail(
            @PathVariable Long conflictId
    ) {
        return ResponseUtils.ok(drugConflictQueryService.getConflictDetail(conflictId));
    }

    // 이 API만 SecurityConfig의 /api/conflicts/** permitAll에서 제외한다 — 경고를 화면에서 지우는
    // 상태 변경이라 토큰 없이 열려 있으면 안 된다. 분석·조회는 기존 permitAll + TODO 패턴을 유지한다.
    //
    // 확인 처리 후 갱신 DTO를 돌려주지 않는 이유: command 커밋과 재조회가 서로 다른 트랜잭션이라
    // 그 사이 재분석이 돌면 방금 확인한 것과 다른 등급이 응답에 실릴 수 있다. 화면 갱신은
    // 프론트가 목록·상세를 다시 조회한다(CQRS의 커맨드/쿼리 분리와도 맞다).
    @Operation(summary = "약물 충돌 확인 처리",
            description = "보호자가 충돌을 확인했음을 기록합니다. 확인한 충돌은 목록에서 제외되며, 상세 조회로는 계속 볼 수 있습니다. "
                    + "이후 위험 등급이 올라가면 자동으로 다시 목록에 나타납니다. 본인이 돌보는 어르신의 충돌이 아니면 404를 반환합니다.")
    @PostMapping("/{conflictId}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolve(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conflictId
    ) {
        drugConflictCommandService.resolve(userId, conflictId);
        return ResponseUtils.noContent();
    }
}