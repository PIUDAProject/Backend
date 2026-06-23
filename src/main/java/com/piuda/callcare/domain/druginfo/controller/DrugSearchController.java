package com.piuda.callcare.domain.druginfo.controller;

import com.piuda.callcare.domain.druginfo.dto.response.DrugAutofillResponse;
import com.piuda.callcare.domain.druginfo.dto.response.DrugSearchResponse;
import com.piuda.callcare.domain.druginfo.service.query.DrugSearchQueryService;
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

@Tag(name = "Drug Search", description = "약품 검색 API (Elasticsearch 기반)")
@RestController
@RequestMapping("/api/search/drugs")
@RequiredArgsConstructor
public class DrugSearchController {

    private final DrugSearchQueryService drugSearchQueryService;

    @Operation(summary = "약 이름 자동완성 검색", description = "Elasticsearch를 이용해 약 이름 자동완성 검색 결과를 반환합니다. 최대 20건.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DrugSearchResponse>>> search(
            @RequestParam String keyword
    ) {
        return ResponseUtils.ok(drugSearchQueryService.search(keyword));
    }

    @Operation(summary = "약 자동 입력 데이터 조회", description = "선택한 약(itemSeq)의 자동 입력 데이터를 반환합니다. memo는 복용방법 + 보관방법 조합으로 자동 생성됩니다.")
    @GetMapping("/{itemSeq}/autofill")
    public ResponseEntity<ApiResponse<DrugAutofillResponse>> autofill(
            @PathVariable String itemSeq
    ) {
        return ResponseUtils.ok(drugSearchQueryService.getAutofill(itemSeq));
    }
}
