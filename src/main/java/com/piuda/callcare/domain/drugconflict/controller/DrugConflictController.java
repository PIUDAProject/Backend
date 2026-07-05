package com.piuda.callcare.domain.drugconflict.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "약물 충돌 상세 조회", description = "카드 클릭 시 충돌 상세(설명 등)를 조회합니다.")
    @GetMapping("/{conflictId}")
    public ResponseEntity<ApiResponse<DrugConflictDetailResponse>> getConflictDetail(
            @PathVariable Long conflictId
    ) {
        return ResponseUtils.ok(drugConflictQueryService.getConflictDetail(conflictId));
    }
}