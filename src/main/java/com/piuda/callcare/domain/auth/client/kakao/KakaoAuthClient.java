package com.piuda.callcare.domain.auth.client.kakao;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.piuda.callcare.domain.auth.dto.response.KakaoTokenResDto;

// 인증/토큰용 (kauth.kakao.com)
@FeignClient(name = "kakaoAuthClient", url = "https://kauth.kakao.com")
public interface KakaoAuthClient {

	@PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	KakaoTokenResDto getAccessToken(
		@RequestParam("grant_type") String grantType,
		@RequestParam("client_id") String clientId,
		@RequestParam("redirect_uri") String redirectUri,
		@RequestParam("code") String code,
		@RequestParam("client_secret") String clientSecret
	);
}
