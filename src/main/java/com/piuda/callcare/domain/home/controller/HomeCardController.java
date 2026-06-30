package com.piuda.callcare.domain.home.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.home.dto.response.HomeCardResponse;
import com.piuda.callcare.domain.home.service.query.HomeCardQueryService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Home", description = "홈 복약 카드 API")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeCardController {

    private final HomeCardQueryService homeCardQueryService;

    @Operation(summary = "홈 복약 카드 조회",
            description = "seniorId와 날짜로 식사시간×병원 그룹별 복약 카드를 조회합니다. date 생략 시 오늘 날짜로 처리됩니다.")
    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<HomeCardResponse>> getHomeCards(
            @RequestParam Long seniorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseUtils.ok(homeCardQueryService.getHomeCards(seniorId, date));
    }
}
