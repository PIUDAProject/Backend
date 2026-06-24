package com.piuda.callcare.domain.auth.dto.response;

import com.piuda.callcare.domain.user.enums.Provider;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record AuthResDto(

	@Schema(description = "유저 아이디")
	Long userId,

	@Schema(description = "액세스 토큰")
	@NotNull
	String accessToken,

	@Schema(description = "리프레시 토큰")
	@NotNull
	String refreshToken,

	@Schema(description = "로그인 제공자")
	@NotNull
	Provider provider,

	@Schema(description = "회원가입 여부 (첫 로그인 여부)")
	@NotNull
	Boolean isNewUser,

	@Schema(description = "액세스 토큰 만료 시간(초)")
	@NotNull
	Integer expiresIn
) {
}
