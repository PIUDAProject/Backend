package com.piuda.callcare.domain.auth.client.kakao;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.piuda.callcare.domain.auth.dto.response.KakaoUserInfoResDto;

// 유저 정보/API용 (kapi.kakao.com)
@FeignClient(name = "kakaoApiClient", url = "https://kapi.kakao.com")
public interface KakaoApiClient {

	@GetMapping("/v2/user/me")
	KakaoUserInfoResDto getUserInfo(@RequestHeader("Authorization") String bearerToken);
}
