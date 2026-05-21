package com.piuda.callcare.global.exception;

import lombok.Getter;

@Getter
public class CallCareException extends RuntimeException {

	private final ErrorCode errorCode;

	public CallCareException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public CallCareException(ErrorCode errorCode, String customMessage) {
		super(customMessage);
		this.errorCode = errorCode;
	}
}
