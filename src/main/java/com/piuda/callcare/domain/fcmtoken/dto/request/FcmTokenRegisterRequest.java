package com.piuda.callcare.domain.fcmtoken.dto.request;

import com.piuda.callcare.domain.fcmtoken.enums.DeviceType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "FCM 토큰 등록 요청")
public record FcmTokenRegisterRequest(

        // 길이 상한은 fcm_token.token 컬럼(512)과 맞춘다. 검증이 없으면 긴 토큰이 400이 아니라
        // DB 제약 위반(500)으로 터져, 클라이언트가 원인을 알 수 없다.
        @Schema(description = "Firebase SDK가 기기에 발급한 FCM 등록 토큰", maxLength = 512)
        @NotBlank @Size(max = 512) String token,

        @Schema(description = "기기 종류 (ANDROID / IOS / WEB)")
        @NotNull DeviceType deviceType
) {}