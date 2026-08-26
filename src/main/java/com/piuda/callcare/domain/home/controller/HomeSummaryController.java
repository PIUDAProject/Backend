package com.piuda.callcare.domain.home.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.home.dto.response.HomeSummaryResponse;
import com.piuda.callcare.domain.home.service.query.HomeSummaryQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Home", description = "홈 복약 카드 API")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeSummaryController {

    private final HomeSummaryQueryService homeSummaryQueryService;

    @Operation(summary = "홈 대시보드 요약 조회",
            description = "상단 요약 카드용 집계입니다. 오늘=예정+완료+다음 복용, 내일=예정만, 어제=완료만 내려갑니다. "
                    + "예정/완료는 아침·점심·저녁 기준 시간대 수(최대 3)입니다. date 생략 시 오늘입니다.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<HomeSummaryResponse>> getSummary(
            @RequestParam Long seniorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseUtils.ok(homeSummaryQueryService.getSummary(seniorId, date));
    }
}
