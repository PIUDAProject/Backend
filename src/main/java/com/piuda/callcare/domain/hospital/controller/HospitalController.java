package com.piuda.callcare.domain.hospital.controller;

import com.piuda.callcare.domain.hospital.dto.response.HospitalSearchResponse;
import com.piuda.callcare.domain.hospital.service.query.HospitalSearchQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Hospital", description = "병원 검색 API (Elasticsearch 기반)")
@Validated
@RestController
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
public class HospitalController {

    private final HospitalSearchQueryService hospitalSearchQueryService;

    @Operation(summary = "병원명 자동완성 검색", description = "Elasticsearch를 이용해 병원명 자동완성 검색 결과를 반환합니다. 최대 20건.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<HospitalSearchResponse>>> search(
            @RequestParam @NotBlank(message = "검색 키워드를 입력해주세요.") String keyword
    ) {
        return ResponseUtils.ok(hospitalSearchQueryService.search(keyword));
    }
}
