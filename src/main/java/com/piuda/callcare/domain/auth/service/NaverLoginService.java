package com.piuda.callcare.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.auth.client.naver.NaverApiClient;
import com.piuda.callcare.domain.auth.client.naver.NaverAuthClient;
import com.piuda.callcare.domain.auth.converter.AuthConverter;
import com.piuda.callcare.domain.auth.dto.response.AuthResDto;
import com.piuda.callcare.domain.auth.dto.response.NaverTokenResDto;
import com.piuda.callcare.domain.auth.dto.response.NaverUserInfoResDto;
import com.piuda.callcare.domain.user.entity.User;
import com.piuda.callcare.domain.user.enums.Provider;
import com.piuda.callcare.domain.user.repository.UserRepository;
import com.piuda.callcare.global.config.redis.RedisRefreshTokenStore;
import com.piuda.callcare.global.exception.CallCareException;
import com.piuda.callcare.global.exception.ErrorCode;
import com.piuda.callcare.global.security.jwt.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class NaverLoginService {

	@Value("${naver.client-id}")
	private String naverClientId;
	@Value("${naver.client-secret}")
	private String naverClientSecret;

	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final NaverAuthClient naverAuthClient;
	private final NaverApiClient naverApiClient;
	private final RedisRefreshTokenStore refreshTokenStore;

	public AuthResDto naverLogin(String code, String state) {
		// 네이버 Access Token 발급
		String naverToken = getNaverAccessToken(code, state);

		// 네이버 사용자 정보 조회
		NaverUserInfoResDto userInfo = getNaverUserInfo(naverToken);

		String providerId = (userInfo.response() != null) ? userInfo.response().id() : null;

		if (providerId == null || providerId.isBlank()) {
			throw new CallCareException(ErrorCode.INVALID_SOCIAL_USER_INFO);
		}

		// 신규 유저 확인
		boolean isNewUser = !userRepository.existsByProviderAndProviderId(Provider.NAVER, providerId);

		User user = userRepository.findByProviderAndProviderId(Provider.NAVER, providerId)
			.orElseGet(() -> userRepository.save(
				User.createSocialUser(extractEmail(userInfo), Provider.NAVER, providerId)
			));

		// JWT 발급
		String accessToken = jwtTokenProvider.createAccessToken(user.getId());
		String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

		// Redis에 리프레시 토큰 저장
		refreshTokenStore.saveRefreshToken(
			String.valueOf(user.getId()),
			refreshToken,
			jwtTokenProvider.getRefreshTokenExpireTime()
		);

		return AuthConverter.toResponse(
			user, accessToken, refreshToken, isNewUser, jwtTokenProvider.getAccessTokenExpireTime()
		);
	}

	// naverAuthClient를 사용하여 네이버로부터 access_token 발급
	private String getNaverAccessToken(String code, String state) {
		NaverTokenResDto token = naverAuthClient.getAccessToken(
			"authorization_code",
			naverClientId,
			naverClientSecret,
			code,
			state
		);

		if (token.error() != null) {
			throw new CallCareException(ErrorCode.INVALID_SOCIAL_USER_INFO, token.errorDescription());
		}

		return token.accessToken();
	}

	// 네이버로부터 사용자 프로필 정보 조회
	private NaverUserInfoResDto getNaverUserInfo(String accessToken) {
		return naverApiClient.getUserInfo("Bearer " + accessToken);
	}

	private String extractEmail(NaverUserInfoResDto userInfo) {
		if (userInfo.response() != null && userInfo.response().email() != null) {
			return userInfo.response().email();
		}
		// 이메일 제공에 동의하지 않은 경우 임시 이메일 생성 (디비 제약조건 대응)
		return userInfo.response().id() + "@naver.user";
	}
}
