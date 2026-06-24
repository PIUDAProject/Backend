package com.piuda.callcare.domain.auth.dto.response;

public record NaverUserInfoResDto(
	String resultcode,
	String message,
	Response response
) {
	public record Response(
		String id,
		String email,
		String nickname,
		String profile_image,
		String mobile
	) {
	}
}
