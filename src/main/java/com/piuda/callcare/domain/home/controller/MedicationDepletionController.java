package com.piuda.callcare.domain.home.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.home.dto.response.MedicationDepletionResponse;
import com.piuda.callcare.domain.home.service.query.MedicationDepletionQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Home", description = "홈 복약 카드 API")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class MedicationDepletionController {

    private final MedicationDepletionQueryService medicationDepletionQueryService;

    @Operation(summary = "복약 부족 목록 조회",
            description = "seniorId의 활성 약 중 남은 복용 일수가 3일 이하로 소진 임박인 약을 남은 일수 오름차순으로 조회합니다. 부족한 약이 없으면 빈 목록을 반환합니다.")
    @GetMapping("/depletion")
    public ResponseEntity<ApiResponse<List<MedicationDepletionResponse>>> getDepletingMedications(
            @RequestParam Long seniorId
    ) {
        return ResponseUtils.ok(medicationDepletionQueryService.getDepletingMedications(seniorId));
    }
}
