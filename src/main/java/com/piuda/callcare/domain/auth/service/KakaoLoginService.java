package com.piuda.callcare.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.piuda.callcare.domain.auth.client.kakao.KakaoApiClient;
import com.piuda.callcare.domain.auth.client.kakao.KakaoAuthClient;
import com.piuda.callcare.domain.auth.converter.AuthConverter;
import com.piuda.callcare.domain.auth.dto.response.AuthResDto;
import com.piuda.callcare.domain.auth.dto.response.KakaoTokenResDto;
import com.piuda.callcare.domain.auth.dto.response.KakaoUserInfoResDto;
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
public class KakaoLoginService {

	@Value("${kakao.client-id}")
	private String kakaoClientId;
	@Value("${kakao.client-secret}")
	private String kakaoClientSecret;
	@Value("${kakao.redirect-uri}")
	private String kakaoRedirectUri;

	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final KakaoAuthClient kakaoAuthClient;
	private final KakaoApiClient kakaoApiClient;
	private final RedisRefreshTokenStore refreshTokenStore;

	public AuthResDto kakaoLogin(String code) {
		// 카카오 Access Token 발급
		String kakaoToken = getKakaoAccessToken(code);

		// 카카오 사용자 정보 조회
		KakaoUserInfoResDto userInfo = getKakaoUserInfo(kakaoToken);

		String providerId = (userInfo.id() != null) ? userInfo.id().toString() : null;

		if (providerId == null || providerId.isBlank()) {
			throw new CallCareException(ErrorCode.INVALID_SOCIAL_USER_INFO);
		}

		// 신규 유저 확인
		boolean isNewUser = !userRepository.existsByProviderAndProviderId(Provider.KAKAO, providerId);

		User user = userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
			.orElseGet(() -> userRepository.save(
				User.createSocialUser(extractEmail(userInfo), Provider.KAKAO, providerId)
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

	// kakaoAuthClient를 사용하여 카카오로부터 access_token 발급
	private String getKakaoAccessToken(String code) {
		KakaoTokenResDto token = kakaoAuthClient.getAccessToken(
			"authorization_code",
			kakaoClientId,
			kakaoRedirectUri,
			code,
			kakaoClientSecret
		);
		return token.accessToken();
	}

	// 카카오로부터 사용자 프로필 정보 조회
	private KakaoUserInfoResDto getKakaoUserInfo(String accessToken) {
		return kakaoApiClient.getUserInfo("Bearer " + accessToken);
	}

	private String extractEmail(KakaoUserInfoResDto userInfo) {
		if (userInfo.kakaoAccount() != null && userInfo.kakaoAccount().email() != null) {
			return userInfo.kakaoAccount().email();
		}
		// 이메일 제공에 동의하지 않은 경우 임시 이메일 생성 (디비 제약조건 대응)
		return userInfo.id() + "@kakao.user";
	}
}
