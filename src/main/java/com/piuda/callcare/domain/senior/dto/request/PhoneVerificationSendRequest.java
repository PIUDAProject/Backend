package com.piuda.callcare.domain.senior.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "전화번호 문자 인증번호 발송 요청")
public record PhoneVerificationSendRequest(

        @Schema(description = "인증번호를 받을 전화번호", example = "01012345678")
        @NotBlank
        @Pattern(regexp = "^01[016789]-?[0-9]{3,4}-?[0-9]{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
        String phoneNumber
) {
}
