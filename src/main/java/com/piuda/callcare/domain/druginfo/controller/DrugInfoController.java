package com.piuda.callcare.domain.druginfo.controller;

import com.piuda.callcare.domain.druginfo.dto.response.DrugSearchResponse;
import com.piuda.callcare.domain.druginfo.service.query.DrugInfoQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Drug", description = "약품 정보 API (MySQL DB 기반 — ES 장애 시 폴백용)")
@Validated
@RestController
@RequestMapping("/api/drugs")
@RequiredArgsConstructor
public class DrugInfoController {

    private final DrugInfoQueryService drugInfoQueryService;

    @Operation(summary = "약 이름으로 검색", description = "DB 캐시에서 약 이름을 검색합니다. 최대 20건 반환.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DrugSearchResponse>>> search(
            @RequestParam @NotBlank(message = "검색 키워드를 입력해주세요.") String keyword
    ) {
        return ResponseUtils.ok(drugInfoQueryService.searchByKeyword(keyword));
    }

    @Operation(summary = "품목기준코드로 약 상세 조회", description = "itemSeq(품목기준코드)로 약품 상세 정보를 조회합니다.")
    @GetMapping("/{itemSeq}")
    public ResponseEntity<ApiResponse<DrugSearchResponse>> getByItemSeq(
            @PathVariable String itemSeq
    ) {
        return ResponseUtils.ok(drugInfoQueryService.getDetailByItemSeq(itemSeq));
    }
}
