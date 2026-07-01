package com.piuda.callcare.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// COMMON
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-001", "잘못된 요청입니다."),
	INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON-002", "요청 파라미터가 올바르지 않습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-003", "리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-004", "지원하지 않는 HTTP 메서드입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON-005", "지원하지 않는 미디어 타입입니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-006", "서버 내부 오류가 발생했습니다."),
	DATA_CONFLICT(HttpStatus.CONFLICT, "COMMON-007", "데이터 충돌이 발생했습니다."),

	// AUTH
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-001", "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH-002", "접근 권한이 없습니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-003", "유효하지 않은 토큰입니다."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-004", "토큰이 만료되었습니다."),
	INVALID_SOCIAL_USER_INFO(HttpStatus.BAD_REQUEST, "AUTH-005", "소셜 로그인 사용자 정보를 가져올 수 없습니다."),

	// USER
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
	DUPLICATED_EMAIL(HttpStatus.CONFLICT, "USER-002", "이미 사용 중인 이메일입니다."),

	// SENIOR
	SENIOR_NOT_FOUND(HttpStatus.NOT_FOUND, "SENIOR-001", "어르신 정보를 찾을 수 없습니다."),

	// DRUG
	DRUG_NOT_FOUND(HttpStatus.NOT_FOUND, "DRUG-001", "약품 정보를 찾을 수 없습니다."),

	// MEDICATION
	MEDICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDICATION-001", "약 정보를 찾을 수 없습니다."),
	MEDICATION_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDICATION-002", "해당 시간대의 복약 스케줄이 없습니다."),
	MEDICATION_LOG_TOGGLE_NOT_TODAY(HttpStatus.BAD_REQUEST, "MEDICATION-003", "오늘 날짜의 복약만 체크할 수 있습니다."),

	// NOTIFICATION
	UNSUPPORTED_MEAL_TIME(HttpStatus.BAD_REQUEST, "NOTI-001", "전화 알림을 지원하지 않는 식사 시간대입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
