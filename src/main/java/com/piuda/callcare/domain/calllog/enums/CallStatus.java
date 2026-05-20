package com.piuda.callcare.domain.calllog.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CallStatus {
    ANSWERED("수신"),
    NO_ANSWER("미수신"),
    FAILED("발신 실패");

    private final String description;
}
