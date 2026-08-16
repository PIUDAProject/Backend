package com.piuda.callcare.domain.fcmtoken.dto.request;

import com.piuda.callcare.domain.fcmtoken.enums.DeviceType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "FCM 토큰 등록 요청")
public record FcmTokenRegisterRequest(

        @Schema(description = "Firebase SDK가 기기에 발급한 FCM 등록 토큰")
        @NotBlank String token,

        @Schema(description = "기기 종류 (ANDROID / IOS / WEB)")
        @NotNull DeviceType deviceType
) {}
