package com.piuda.callcare.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.piuda.callcare.domain.auth.dto.response.AuthResDto;
import com.piuda.callcare.domain.auth.service.KakaoLoginService;
import com.piuda.callcare.domain.auth.service.NaverLoginService;
import com.piuda.callcare.global.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "회원/인증 API")
public class AuthController {

	private final KakaoLoginService kakaoLoginService;
	private final NaverLoginService naverLoginService;

	@PostMapping("/oauth/kakao")
	@Operation(summary = "카카오 회원가입/로그인 API", description = "인가코드(code)로 카카오 토큰 교환 후, 우리 서비스 JWT 발급")
	public ResponseEntity<ApiResponse<AuthResDto>> kakaoLogin(
		@RequestParam("code") @NotBlank String code
	) {
		AuthResDto res = kakaoLoginService.kakaoLogin(code);
		return ResponseEntity.ok(ApiResponse.success(res));
	}

	@PostMapping("/oauth/naver")
	@Operation(summary = "네이버 회원가입/로그인 API", description = "인가코드(code)와 state로 네이버 토큰 교환 후, 우리 서비스 JWT 발급")
	public ResponseEntity<ApiResponse<AuthResDto>> naverLogin(
		@RequestParam("code") @NotBlank String code,
		@RequestParam("state") @NotBlank String state
	) {
		AuthResDto res = naverLoginService.naverLogin(code, state);
		return ResponseEntity.ok(ApiResponse.success(res));
	}
}
