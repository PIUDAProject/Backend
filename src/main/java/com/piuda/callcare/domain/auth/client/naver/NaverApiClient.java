package com.piuda.callcare.domain.auth.client.naver;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.piuda.callcare.domain.auth.dto.response.NaverUserInfoResDto;

// 유저 정보/API용 (openapi.naver.com)
@FeignClient(name = "naverApiClient", url = "https://openapi.naver.com")
public interface NaverApiClient {

	@GetMapping("/v1/nid/me")
	NaverUserInfoResDto getUserInfo(@RequestHeader("Authorization") String bearerToken);
}
