package com.piuda.callcare.domain.senior.controller;

import com.piuda.callcare.domain.senior.dto.request.PhoneVerificationSendRequest;
import com.piuda.callcare.domain.senior.dto.request.SeniorCreateRequest;
import com.piuda.callcare.domain.senior.dto.request.SeniorMealTimeUpdateRequest;
import com.piuda.callcare.domain.senior.dto.response.SeniorResponse;
import com.piuda.callcare.domain.senior.service.PhoneVerificationService;
import com.piuda.callcare.domain.senior.service.command.SeniorCommandService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Senior", description = "부모님 정보 API")
@RestController
@RequestMapping("/api/seniors")
@RequiredArgsConstructor
public class SeniorController {

    private final SeniorCommandService seniorCommandService;
    private final PhoneVerificationService phoneVerificationService;

    @Operation(
            summary = "전화번호 인증번호 발송",
            description = "부모님 전화번호로 6자리 SMS 인증번호를 발송합니다. 인증번호는 3분간 유효하며, 부모님 정보 등록 시 verificationCode로 검증합니다."
    )
    @PostMapping("/phone-verification")
    public ResponseEntity<ApiResponse<Void>> sendPhoneVerificationCode(
            @RequestBody @Valid PhoneVerificationSendRequest request
    ) {
        phoneVerificationService.sendVerificationCode(request.phoneNumber());
        return ResponseUtils.ok();
    }

    @Operation(
            summary = "부모님 정보 등록",
            description = "부모님 성함/성별/생년월일/전화번호와 생활 시간(아침/점심/저녁 식사 시간)을 등록합니다. "
                    + "프론트는 사용자가 시간을 직접 수정하지 않은 기본 상태에서도 기본값(08:00 / 12:00 / 18:00)을 포함해 전송합니다. "
                    + "서버는 누락된 값이 있으면 동일한 기본값으로 보정해 저장합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<SeniorResponse>> register(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid SeniorCreateRequest request
    ) {
        return ResponseUtils.created(seniorCommandService.register(userId, request));
    }

    @Operation(
            summary = "생활 시간(식사 시간) 수정",
            description = "마이페이지에서 아침/점심/저녁 식사 시간을 수정합니다. 전화 알림 시간 계산 기준으로 사용됩니다."
    )
    @PatchMapping("/{seniorId}/meal-time")
    public ResponseEntity<ApiResponse<SeniorResponse>> updateMealTime(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long seniorId,
            @RequestBody @Valid SeniorMealTimeUpdateRequest request
    ) {
        return ResponseUtils.ok(seniorCommandService.updateMealTime(userId, seniorId, request));
    }
}
