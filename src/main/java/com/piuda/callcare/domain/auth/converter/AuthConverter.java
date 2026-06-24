package com.piuda.callcare.domain.auth.converter;

import com.piuda.callcare.domain.auth.dto.response.AuthResDto;
import com.piuda.callcare.domain.user.entity.User;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AuthConverter {

	public static AuthResDto toResponse(User user, String accessToken, String refreshToken,
		boolean isNewUser, long accessExpMs) {
		return AuthResDto.builder()
			.userId(user.getId())
			.accessToken(accessToken)
			.refreshToken(refreshToken)
			.provider(user.getProvider())
			.isNewUser(isNewUser)
			.expiresIn((int) (accessExpMs / 1000)) // 초 단위
			.build();
	}
}
