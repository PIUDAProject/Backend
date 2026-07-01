package com.piuda.callcare.domain.home.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.home.dto.request.MedicationLogToggleRequest;
import com.piuda.callcare.domain.home.dto.response.MedicationLogToggleResponse;
import com.piuda.callcare.domain.home.service.command.MedicationToggleCommandService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Home", description = "홈 복약 카드 API")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class MedicationLogToggleController {

    private final MedicationToggleCommandService medicationToggleCommandService;

    @Operation(summary = "복약 완료 토글",
            description = "오늘 날짜 기준으로 특정 약·시간대의 복용 완료 여부를 토글하고, 해당 시간대의 재계산된 완료 상태를 반환합니다.")
    @PostMapping("/medications/{medicationId}/logs/toggle")
    public ResponseEntity<ApiResponse<MedicationLogToggleResponse>> toggle(
            @PathVariable Long medicationId,
            @Valid @RequestBody MedicationLogToggleRequest request
    ) {
        return ResponseUtils.ok(
                medicationToggleCommandService.toggle(medicationId, request.mealTime(), request.date()));
    }
}