package com.piuda.callcare.domain.auth.converter;

import org.springframework.stereotype.Component;

import com.piuda.callcare.domain.auth.dto.response.AuthResDto;
import com.piuda.callcare.domain.user.entity.User;

@Component
public class AuthConverter {

	public AuthResDto toResponse(User user, String accessToken, String refreshToken,
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
