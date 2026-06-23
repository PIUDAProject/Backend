package com.piuda.callcare.domain.druginfo.controller;

import com.piuda.callcare.domain.druginfo.dto.response.DrugSearchResponse;
import com.piuda.callcare.domain.druginfo.service.query.DrugInfoQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Drug", description = "약품 정보 API (DB 캐시 기반)")
@RestController
@RequestMapping("/api/drugs")
@RequiredArgsConstructor
public class DrugInfoController {

    private final DrugInfoQueryService drugInfoQueryService;

    @Operation(summary = "약 이름으로 검색", description = "DB 캐시에서 약 이름을 검색합니다. 결과가 없으면 식약처 API를 호출해 저장 후 반환합니다.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DrugSearchResponse>>> search(
            @RequestParam String keyword
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
