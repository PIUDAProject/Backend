package com.piuda.callcare.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Provider {
	KAKAO("카카오"),
	NAVER("네이버");

	private final String description;
}