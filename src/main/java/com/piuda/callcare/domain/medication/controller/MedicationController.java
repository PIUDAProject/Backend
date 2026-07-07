package com.piuda.callcare.domain.medication.controller;

import com.piuda.callcare.domain.medication.dto.request.MedicationCreateRequest;
import com.piuda.callcare.domain.medication.dto.response.MedicationResponse;
import com.piuda.callcare.domain.medication.service.command.MedicationCommandService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Medication", description = "약 등록 API")
@RestController
@RequestMapping("/api/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationCommandService medicationCommandService;

    @Operation(summary = "약 등록", description = "약을 1개 이상 등록합니다. 단건이면 리스트에 1개, 여러 개면 여러 개 담아서 보내세요. 하나라도 실패하면 전체 롤백됩니다.")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<MedicationResponse>>> registerBatch(
        @AuthenticationPrincipal Long userId,
        @RequestBody @Valid List<MedicationCreateRequest> requests
    ) {
        // TODO: 인증 필터 도입 후 제거. 현재는 로컬 테스트를 위해 userId 임시 하드코딩
        Long testUserId = (userId != null) ? userId : 1L;
        return ResponseUtils.created(medicationCommandService.registerBatch(testUserId, requests));
    }
}
