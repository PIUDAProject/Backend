package com.piuda.callcare.domain.senior.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전화번호 인증번호 발송 결과")
public record PhoneVerificationSendResponse(
        @Schema(description = "Mock 모드에서만 반환되는 6자리 인증번호", example = "123456", nullable = true)
        String verificationCode
) {
}
