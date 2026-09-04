package com.piuda.callcare.domain.druginfo.controller;

import com.piuda.callcare.domain.druginfo.converter.DrugInfoConverter;
import com.piuda.callcare.domain.druginfo.dto.response.DrugReindexStartResponse;
import com.piuda.callcare.domain.druginfo.dto.response.DrugSyncHistoryResponse;
import com.piuda.callcare.domain.druginfo.service.command.DrugReindexService;
import com.piuda.callcare.domain.druginfo.service.query.DrugSyncHistoryQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Drug Admin", description = "약품 검색 인덱스 재색인 (관리자용)")
@RestController
@RequestMapping("/api/admin/drugs")
@RequiredArgsConstructor
@Validated
public class DrugAdminController {

    private final DrugReindexService drugReindexService;
    private final DrugSyncHistoryQueryService drugSyncHistoryQueryService;
    private final DrugInfoConverter drugInfoConverter;

    @Operation(
            summary = "약품 검색 인덱스 재색인",
            description = "MySQL drug_info를 새 물리 인덱스에 재색인하고 alias를 무중단 스왑합니다. "
                    + "백그라운드에서 실행되며 이력 ID를 즉시 반환합니다. 이미 실행 중이면 409를 반환합니다."
    )
    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<DrugReindexStartResponse>> reindex() {
        DrugReindexService.ReindexStartResult result = drugReindexService.startReindex();
        return ResponseUtils.ok(new DrugReindexStartResponse(result.historyId(), result.status()));
    }

    @Operation(summary = "재색인 이력 목록 조회", description = "최근 재색인 이력을 최신순으로 반환합니다.")
    @GetMapping("/reindex/history")
    public ResponseEntity<ApiResponse<List<DrugSyncHistoryResponse>>> history(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        List<DrugSyncHistoryResponse> histories = drugSyncHistoryQueryService.getRecentHistories(limit)
                .stream()
                .map(drugInfoConverter::toSyncHistoryResponse)
                .toList();
        return ResponseUtils.ok(histories);
    }

    @Operation(summary = "재색인 진행 상태 조회", description = "이력 ID로 특정 재색인의 상태를 조회합니다.")
    @GetMapping("/reindex/{historyId}")
    public ResponseEntity<ApiResponse<DrugSyncHistoryResponse>> historyDetail(
            @PathVariable Long historyId
    ) {
        DrugSyncHistoryResponse response = drugInfoConverter.toSyncHistoryResponse(
                drugSyncHistoryQueryService.getHistory(historyId));
        return ResponseUtils.ok(response);
    }
}
