package com.piuda.callcare.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
	private final AuthConverter authConverter;

	public AuthResDto kakaoLogin(String code) {
		String kakaoToken = getKakaoAccessToken(code);
		KakaoUserInfoResDto userInfo = getKakaoUserInfo(kakaoToken);

		String providerId = (userInfo.id() != null) ? userInfo.id().toString() : null;

		if (providerId == null || providerId.isBlank()) {
			throw new CallCareException(ErrorCode.INVALID_SOCIAL_USER_INFO);
		}

		// 신규 유저 조회/생성을 단일 경로로 처리 (동시 요청에 의한 중복 생성/오판 방지)
		boolean[] isNewUserHolder = new boolean[1];
		User user = findOrCreateUser(providerId, userInfo, isNewUserHolder);
		boolean isNewUser = isNewUserHolder[0];

		// JWT 발급
		String accessToken = jwtTokenProvider.createAccessToken(user.getId());
		String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

		// Redis에 리프레시 토큰 저장은 DB 트랜잭션 커밋 이후로 지연
		// (커밋 실패 시 Redis에만 토큰이 남는 상태 불일치 방지)
		Long userId = user.getId();
		long refreshExpireMs = jwtTokenProvider.getRefreshTokenExpireTime();
		registerAfterCommitRefreshTokenSave(userId, refreshToken, refreshExpireMs);

		return authConverter.toResponse(
			user, accessToken, refreshToken, isNewUser, jwtTokenProvider.getAccessTokenExpireTime()
		);
	}

	// provider + providerId 유니크 제약을 기준으로 처리
	private User findOrCreateUser(String providerId, KakaoUserInfoResDto userInfo, boolean[] isNewUserHolder) {
		return userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
			.map(existing -> {
				isNewUserHolder[0] = false;
				return existing;
			})
			.orElseGet(() -> {
				try {
					User saved = userRepository.save(
						User.createSocialUser(extractEmail(userInfo), Provider.KAKAO, providerId)
					);
					isNewUserHolder[0] = true;
					return saved;
				} catch (DataIntegrityViolationException e) {
					// 동시 요청으로 다른 트랜잭션이 먼저 생성한 경우 재조회로 수렴
					isNewUserHolder[0] = false;
					return userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
						.orElseThrow(() -> e);
				}
			});
	}

	// 현재 트랜잭션이 커밋된 이후에만 Redis에 리프레시 토큰을 저장
	private void registerAfterCommitRefreshTokenSave(Long userId, String refreshToken, long refreshExpireMs) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				refreshTokenStore.saveRefreshToken(String.valueOf(userId), refreshToken, refreshExpireMs);
			}
		});
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

	private KakaoUserInfoResDto getKakaoUserInfo(String accessToken) {
		return kakaoApiClient.getUserInfo("Bearer " + accessToken);
	}

	private String extractEmail(KakaoUserInfoResDto userInfo) {
		if (userInfo.kakaoAccount() != null && userInfo.kakaoAccount().email() != null) {
			return userInfo.kakaoAccount().email();
		}
		// 임시 이메일 생성 (디비 제약조건 대응)
		return userInfo.id() + "@kakao.user";
	}
}

