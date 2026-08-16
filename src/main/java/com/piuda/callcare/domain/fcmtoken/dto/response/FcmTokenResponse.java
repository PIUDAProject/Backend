package com.piuda.callcare.domain.fcmtoken.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FCM 토큰 등록 응답")
public record FcmTokenResponse(

        @Schema(description = "FCM 토큰 ID — 같은 토큰을 다시 등록하면 같은 값이 반환됩니다")
        Long fcmTokenId,

        @Schema(description = "발송 대상 여부")
        Boolean isActive
) {}
