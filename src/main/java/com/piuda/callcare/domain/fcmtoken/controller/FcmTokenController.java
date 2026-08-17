package com.piuda.callcare.domain.fcmtoken.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.fcmtoken.dto.request.FcmTokenDeactivateRequest;
import com.piuda.callcare.domain.fcmtoken.dto.request.FcmTokenRegisterRequest;
import com.piuda.callcare.domain.fcmtoken.dto.response.FcmTokenResponse;
import com.piuda.callcare.domain.fcmtoken.service.command.FcmTokenCommandService;
import com.piuda.callcare.global.common.response.ApiResponse;
import com.piuda.callcare.global.common.response.ResponseUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "FcmToken", description = "FCM 푸시 토큰 등록/해제 API")
@RestController
@RequestMapping("/api/fcm-tokens")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenCommandService fcmTokenCommandService;

    @Operation(
        summary = "FCM 토큰 등록",
        description = "로그인 직후와 토큰 갱신 시 호출합니다. 같은 토큰을 다시 보내도 행이 늘어나지 않고 같은 fcmTokenId가 반환됩니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<FcmTokenResponse>> register(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid FcmTokenRegisterRequest request
    ) {
        return ResponseUtils.created(fcmTokenCommandService.register(userId, request));
    }

    @Operation(
        summary = "FCM 토큰 해제",
        description = "로그아웃 시 호출해 해당 기기를 푸시 발송 대상에서 제외합니다. 이미 해제됐거나 없는 토큰이어도 200을 반환합니다."
    )
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid FcmTokenDeactivateRequest request
    ) {
        fcmTokenCommandService.deactivate(userId, request.token());
        return ResponseUtils.ok();
    }
}
