package com.piuda.callcare.domain.auth.client.naver;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.piuda.callcare.domain.auth.dto.response.NaverTokenResDto;

// 인증/토큰용 (nid.naver.com)
@FeignClient(name = "naverAuthClient", url = "https://nid.naver.com")
public interface NaverAuthClient {

	@GetMapping("/oauth2.0/token")
	NaverTokenResDto getAccessToken(
		@RequestParam("grant_type") String grantType,
		@RequestParam("client_id") String clientId,
		@RequestParam("client_secret") String clientSecret,
		@RequestParam("code") String code,
		@RequestParam("state") String state
	);
}
