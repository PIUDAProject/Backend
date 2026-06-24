package com.piuda.callcare.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserInfoResDto(
	Long id,
	@JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
	public record KakaoAccount(
		String email,
		Profile profile
	) {
		public record Profile(
			String nickname,
			@JsonProperty("thumbnail_image_url") String profileImg
		) {
		}
	}
}
