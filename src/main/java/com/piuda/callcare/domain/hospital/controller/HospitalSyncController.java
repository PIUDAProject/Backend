package com.piuda.callcare.domain.hospital.controller;

import com.piuda.callcare.domain.hospital.converter.HospitalConverter;
import com.piuda.callcare.domain.hospital.dto.response.HospitalSyncHistoryResponse;
import com.piuda.callcare.domain.hospital.dto.response.HospitalSyncStartResponse;
import com.piuda.callcare.domain.hospital.service.command.HospitalSyncCommandService;
import com.piuda.callcare.domain.hospital.service.query.HospitalSyncHistoryQueryService;
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

@Tag(name = "Hospital Sync", description = "병원 공공데이터 수집 배치 (관리자용)")
@RestController
@RequestMapping("/api/admin/hospitals/sync")
@RequiredArgsConstructor
@Validated
public class HospitalSyncController {

    private final HospitalSyncCommandService hospitalSyncCommandService;
    private final HospitalSyncHistoryQueryService hospitalSyncHistoryQueryService;
    private final HospitalConverter hospitalConverter;

    @Operation(summary = "병원 공공데이터 수동 동기화", description = "동기화를 백그라운드에서 시작하고 이력 ID를 즉시 반환합니다. 진행 상황은 이력 조회 API로 확인합니다. maxPages를 주면 테스트용으로 해당 페이지까지만 수집합니다. 이미 실행 중인 동기화가 있으면 409를 반환합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<HospitalSyncStartResponse>> sync(
            @RequestParam(required = false) @Min(1) Integer maxPages
    ) {
        HospitalSyncCommandService.SyncStartResult result = hospitalSyncCommandService.startSync(maxPages);
        return ResponseUtils.ok(hospitalConverter.toSyncStartResponse(result));
    }

    @Operation(summary = "병원 데이터 수집 이력 조회", description = "최근 수집 배치 이력을 최신순으로 반환합니다.")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<HospitalSyncHistoryResponse>>> history(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        List<HospitalSyncHistoryResponse> histories = hospitalSyncHistoryQueryService.getRecentHistories(limit)
                .stream()
                .map(hospitalConverter::toSyncHistoryResponse)
                .toList();
        return ResponseUtils.ok(histories);
    }

    @Operation(summary = "중단된 병원 동기화 이력 실패 처리", description = "프로세스 종료 등으로 RUNNING에 남은 이력을 FAILED로 변경합니다. 실제 동기화가 실행 중이지 않은지 확인한 뒤 사용해야 합니다.")
    @PostMapping("/history/{historyId}/fail")
    public ResponseEntity<ApiResponse<Void>> failRunningHistory(
            @PathVariable Long historyId
    ) {
        hospitalSyncCommandService.failRunningHistory(historyId);
        return ResponseUtils.ok();
    }
}
