package com.piuda.callcare.domain.medication.controller;

import com.piuda.callcare.domain.medication.dto.request.MedicationCreateRequest;
import com.piuda.callcare.domain.medication.dto.request.MedicationUpdateRequest;
import com.piuda.callcare.domain.medication.dto.response.MedicationDetailResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationGroupItemResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationNoteGroupResponse;
import com.piuda.callcare.domain.medication.dto.response.MedicationResponse;
import com.piuda.callcare.domain.medication.service.command.MedicationCommandService;
import com.piuda.callcare.domain.medication.service.query.MedicationQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Medication", description = "약 등록 API")
@RestController
@RequestMapping("/api/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationCommandService medicationCommandService;
    private final MedicationQueryService medicationQueryService;

    @Operation(summary = "약 등록", description = "약을 1개 이상 등록합니다. 단건이면 리스트에 1개, 여러 개면 여러 개 담아서 보내세요. 하나라도 실패하면 전체 롤백됩니다.")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<MedicationResponse>>> registerBatch(
        @AuthenticationPrincipal Long userId,
        @RequestBody @Valid List<MedicationCreateRequest> requests
    ) {
        return ResponseUtils.created(medicationCommandService.registerBatch(userId, requests));
    }

    @Operation(
        summary = "약 단건 상세 조회",
        description = "약물노트에서 '재등록' 버튼 클릭 시 호출합니다. 약 이름·복용법·병원명·처방일·메모 등 등록 화면 프리필에 필요한 모든 필드를 반환합니다. 반환된 값을 그대로 POST /api/medications/batch에 담아 재등록하면 됩니다."
    )
    @GetMapping("/{medicationId}")
    public ResponseEntity<ApiResponse<MedicationDetailResponse>> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long medicationId
    ) {
        return ResponseUtils.ok(medicationQueryService.getDetail(userId, medicationId));
    }

    @Operation(summary = "약물노트 그룹 상세 조회", description = "병원+처방일 그룹 카드 클릭 시 해당 그룹의 약 리스트와 각 약의 메모를 반환합니다. hospitalName/prescriptionDate 미전달 시 null 그룹 조회.")
    @GetMapping("/group")
    public ResponseEntity<ApiResponse<List<MedicationGroupItemResponse>>> getGroup(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long seniorId,
            @RequestParam(required = false) String hospitalName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate prescriptionDate
    ) {
        return ResponseUtils.ok(medicationQueryService.getGroup(userId, seniorId, hospitalName, prescriptionDate));
    }

    @Operation(summary = "약물노트 목록 조회", description = "복용 시작일 + 병원 기준으로 그룹화된 약물 목록을 반환합니다.")
    @GetMapping("/notes")
    public ResponseEntity<ApiResponse<List<MedicationNoteGroupResponse>>> getNoteList(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long seniorId
    ) {
        return ResponseUtils.ok(medicationQueryService.getNoteList(userId, seniorId));
    }

    @Operation(summary = "약물노트 검색", description = "약 이름/별명/병원명으로 검색합니다. period: 1w·1m·3m·1y (기본 1y)")
    @GetMapping("/notes/search")
    public ResponseEntity<ApiResponse<List<MedicationNoteGroupResponse>>> searchNotes(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long seniorId,
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "1y") String period
    ) {
        return ResponseUtils.ok(medicationQueryService.searchNotes(userId, seniorId, keyword, period));
    }

    @Operation(summary = "약 수정", description = "null 필드는 변경하지 않습니다. timesPerDay 변경 시 스케줄을 재생성합니다.")
    @PatchMapping("/{medicationId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long medicationId,
            @RequestBody @Valid MedicationUpdateRequest request
    ) {
        medicationCommandService.update(userId, medicationId, request);
        return ResponseUtils.ok();
    }

    @Operation(summary = "약 삭제", description = "약과 복용 스케줄을 완전 삭제합니다. 복구 불가능합니다.")
    @DeleteMapping("/{medicationId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long medicationId
    ) {
        medicationCommandService.delete(userId, medicationId);
        return ResponseUtils.noContent();
    }
}
