package com.piuda.callcare.domain.fcmtoken.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FCM 토큰 해제 요청.
 * <p>
 * 토큰을 query param이 아니라 요청 본문으로 받는다 — URL에 실으면 Nginx 접근 로그·프록시·브라우저 히스토리에
 * <b>기기 식별자가 그대로 남기</b> 때문이다. 토큰은 비밀값은 아니지만 기기를 특정하는 값이라 무기한 잔존은 피한다.
 */
@Schema(description = "FCM 토큰 해제 요청")
public record FcmTokenDeactivateRequest(

        @Schema(description = "해제할 FCM 등록 토큰", maxLength = 512)
        @NotBlank @Size(max = 512) String token
) {}