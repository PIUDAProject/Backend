package com.piuda.callcare.domain.medication.controller;

import com.piuda.callcare.domain.medication.dto.request.MedicationCreateRequest;
import com.piuda.callcare.domain.medication.dto.response.MedicationGroupItemResponse;
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
}
